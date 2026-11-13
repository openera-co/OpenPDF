package com.reel.editor.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.reel.editor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the process alive and shows progress while an export runs in the background. */
class ExportService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.export_channel), NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0
        try {
            ServiceCompat.startForeground(this, ID, notification(0, true), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            Exporter.state.collectLatest { s ->
                when (s) {
                    is ExportState.Running -> nm.notify(ID, notification((s.progress * 100).toInt(), true))
                    else -> { ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(progress: Int, ongoing: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Exporting video")
            .setProgress(100, progress, progress == 0)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "export"
        private const val ID = 7

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
            } catch (_: Exception) {
                // Background start restrictions: export still runs while the app is visible.
            }
        }
    }
}
