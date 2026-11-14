package com.reel.editor.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads text aloud into a WAV file with the phone's own text-to-speech voice. */
object Speech {
    private suspend fun engine(context: Context): TextToSpeech = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) cont.resume(tts!!)
            else { tts?.shutdown(); cont.resumeWithException(IllegalStateException("No text-to-speech voice on this phone")) }
        }
        cont.invokeOnCancellation { tts?.shutdown() }
    }

    suspend fun synthesize(context: Context, text: String, out: File, rate: Float = 1f, pitch: Float = 1f): File {
        val tts = withTimeout(10_000) { engine(context) }
        try {
            tts.language = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            out.parentFile?.mkdirs()
            withTimeout(60_000) {
                suspendCancellableCoroutine { cont ->
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { if (cont.isActive) cont.resume(Unit) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException("Couldn't read that text")) }
                    })
                    val r = tts.synthesizeToFile(text, null, out, "reel-tts")
                    if (r != TextToSpeech.SUCCESS && cont.isActive) cont.resumeWithException(IllegalStateException("Couldn't read that text"))
                }
            }
            if (!out.exists() || out.length() < 100) throw IllegalStateException("Couldn't read that text")
            return out
        } finally {
            tts.shutdown()
        }
    }
}
