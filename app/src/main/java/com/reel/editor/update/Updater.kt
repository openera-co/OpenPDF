package com.reel.editor.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.reel.editor.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val apk: Asset,
    val patches: List<Patch> = emptyList(),
) {
    @Serializable data class Asset(val url: String, val size: Long, val sha256: String)
    @Serializable data class Patch(val from: Int, val url: String, val size: Long, val sourceSha256: String = "")
}

/**
 * In-app updates. Looks at an update.json, and when a newer version is there downloads only a small
 * patch for the installed version, rebuilds the new APK from the installed one, checks its SHA-256
 * and hands it to the system installer. Falls back to the full APK when no patch fits.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo, val downloadBytes: Long, val patched: Boolean) : State
        data class Downloading(val info: UpdateInfo, val fraction: Float) : State
        data object Verifying : State
        data object Installing : State
        data class Failed(val message: String) : State
        /** Android needs the "install unknown apps" switch for Reel before it can update itself. */
        data object NeedsPermission : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREFS = "updates"
    /** Updates outlive the screen that started them: closing a dialog or leaving Home doesn't stop one. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun checkAsync(context: Context, quiet: Boolean = false) { scope.launch { check(context.applicationContext, quiet) } }
    fun installAsync(context: Context) { scope.launch { install(context.applicationContext) } }

    /** Leftovers from an update that already went in. */
    fun cleanUp(context: Context) {
        scope.launch(Dispatchers.IO) { File(context.cacheDir, "update").deleteRecursively() }
    }

    fun url(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.UPDATE_URL

    fun setUrl(context: Context, url: String) = context.getSharedPreferences(PREFS, 0).edit().putString("url", url.trim()).apply()

    private fun installedVersion(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }

    /** Fetches the manifest; [quiet] keeps "up to date" and errors out of the UI (launch check). */
    suspend fun check(context: Context, quiet: Boolean = false) {
        val u = url(context)
        if (u.isBlank()) { if (!quiet) _state.value = State.Failed("No update source set"); return }
        if (!quiet) _state.value = State.Checking
        try {
            val info = withContext(Dispatchers.IO) { json.decodeFromString<UpdateInfo>(fetchText(resolve(u, u))) }
            val current = installedVersion(context)
            if (info.versionCode <= current) { _state.value = if (quiet) State.Idle else State.UpToDate; return }
            val patch = info.patches.firstOrNull { it.from == current }
            _state.value = State.Available(info, patch?.size ?: info.apk.size, patch != null)
        } catch (e: Exception) {
            _state.value = if (quiet) State.Idle else State.Failed(e.message ?: "Can't reach the update source")
        }
    }

    fun dismiss() { _state.value = State.Idle; pending = null }

    /** Kept while the user is away turning on "install unknown apps". */
    private var pending: State.Available? = null

    /** Back from settings: carry on if the switch is now on. */
    fun resume(context: Context) {
        if (_state.value != State.NeedsPermission) return
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) installAsync(context)
    }

    /** Downloads (patch or full APK), verifies and starts the install. */
    suspend fun install(context: Context) {
        val avail = (_state.value as? State.Available) ?: (pending.takeIf { _state.value == State.NeedsPermission }) ?: return
        val info = avail.info
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            pending = avail
            _state.value = State.NeedsPermission
            app.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        pending = null
        // Rebuilding needs room for the whole new APK next to the installed one.
        val need = info.apk.size + (64L shl 20)
        val free = app.cacheDir.usableSpace
        if (free < need) { _state.value = State.Failed("Needs ${need / 1_000_000} MB free space"); return }
        val dir = File(app.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "reel-${info.versionName}.apk")
        try {
            val base = url(app)
            val patch = info.patches.firstOrNull { it.from == installedVersion(app) }
            withContext(Dispatchers.IO) {
                if (patch != null) {
                    val p = File(dir, "update.rpatch")
                    download(resolve(base, patch.url), p, patch.size) { _state.value = State.Downloading(info, it) }
                    _state.value = State.Verifying
                    try {
                        applyPatch(File(app.applicationInfo.sourceDir), p, apk)
                    } catch (e: Exception) {
                        apk.delete()
                    }
                    p.delete()
                    // A patch that doesn't rebuild the exact APK (say, a reinstalled build) falls back to the full file.
                    if (!apk.exists() || sha256(apk) != info.apk.sha256) {
                        download(resolve(base, info.apk.url), apk, info.apk.size) { _state.value = State.Downloading(info, it) }
                    }
                } else {
                    download(resolve(base, info.apk.url), apk, info.apk.size) { _state.value = State.Downloading(info, it) }
                }
                _state.value = State.Verifying
                if (sha256(apk) != info.apk.sha256) throw IllegalStateException("Download was damaged, try again")
            }
            _state.value = State.Installing
            withContext(Dispatchers.IO) { startInstall(app, apk) }
        } catch (e: Exception) {
            apk.delete()
            _state.value = State.Failed(e.message ?: "Update failed")
        }
    }

    // ------------------------------------------------------------------ patching

    /**
     * Rebuilds a file from [old] and an RPT1 patch (see tools/update/make_update.py): 'C' copies a
     * range of the installed APK, 'D' writes literal bytes, 'E' ends.
     */
    fun applyPatch(old: File, patch: File, out: File) {
        RandomAccessFile(old, "r").use { src ->
            patch.inputStream().buffered().use { raw ->
                val magic = ByteArray(4)
                DataInputStream(raw).readFully(magic)
                require(String(magic) == "RPT1") { "Not an update patch" }
                val ops = DataInputStream(InflaterInputStream(raw, java.util.zip.Inflater(), 1 shl 16).buffered(1 shl 16))
                FileOutputStream(out).buffered(1 shl 20).use { dst ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        when (val op = ops.read()) {
                            'E'.code -> return
                            'C'.code -> {
                                val off = ops.readLongLe()
                                var left = ops.readLongLe()
                                require(off >= 0 && off + left <= src.length()) { "Patch doesn't match this version" }
                                src.seek(off)
                                while (left > 0) {
                                    val n = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                    if (n < 0) throw EOFException()
                                    dst.write(buf, 0, n); left -= n
                                }
                            }
                            'D'.code -> {
                                var left = ops.readLongLe()
                                while (left > 0) {
                                    val n = ops.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                    if (n < 0) throw EOFException()
                                    dst.write(buf, 0, n); left -= n
                                }
                            }
                            -1 -> throw EOFException("Patch ended early")
                            else -> throw IllegalStateException("Bad patch op $op")
                        }
                    }
                }
            }
        }
    }

    private fun DataInputStream.readLongLe(): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((readUnsignedByte().toLong()) shl (8 * i))
        return v
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(1 shl 20); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------------ network

    /** Relative URLs in update.json are resolved against the manifest's own URL. */
    private fun resolve(base: String, u: String) = URL(URL(base), u).toString()

    private fun open(u: String): HttpURLConnection {
        var url = URL(u)
        repeat(5) {
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Reel/${BuildConfig.VERSION_NAME}")
            }
            // Follow redirects by hand so https -> other host (release CDNs) works.
            if (c.responseCode in 300..399) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); return@repeat }
            if (c.responseCode !in 200..299) throw IllegalStateException("Server said ${c.responseCode}")
            return c
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun fetchText(u: String): String = open(u).let { c -> c.inputStream.use { it.readBytes().decodeToString() }.also { c.disconnect() } }

    private fun download(u: String, to: File, expected: Long, progress: (Float) -> Unit) {
        val c = open(u)
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: expected
            c.inputStream.use { input: InputStream ->
                FileOutputStream(to).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 256 * 1024) { last = done; progress((done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ install

    private fun startInstall(context: Context, apk: File) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(apk.length())
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("reel.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out, 1 shl 20) }; s.fsync(out) }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    internal fun onInstallResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system asks the user to confirm; show its screen.
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> { _state.value = State.Idle; cleanUp(context) }
            else -> {
                cleanUp(context)
                _state.value = State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install was cancelled")
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallResult(context, intent)
}
