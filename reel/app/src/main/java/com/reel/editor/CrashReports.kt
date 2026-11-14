package com.reel.editor

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

/**
 * Crash and error reporting (Sentry). Only release builds have a DSN, so debug and test runs
 * never report. No personal data, screenshots or screen recordings are sent.
 */
object CrashReports {
    val enabled: Boolean get() = BuildConfig.SENTRY_DSN.isNotEmpty()

    fun start(context: Context) {
        if (!enabled) return
        SentryAndroid.init(context) { o ->
            o.dsn = BuildConfig.SENTRY_DSN
            o.release = "reel@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            o.environment = "production"
            o.isSendDefaultPii = false
            o.isAttachScreenshot = false
            o.isAttachViewHierarchy = false
            o.isAnrEnabled = true
            o.isEnableNdk = true
            o.tracesSampleRate = 0.2
            o.sessionReplay.sessionSampleRate = 0.0
            o.sessionReplay.onErrorSampleRate = 0.0
        }
    }

    /** A handled failure worth knowing about (export, captions, preview, effects). */
    fun error(where: String, t: Throwable) {
        if (!enabled) return
        Sentry.withScope { s ->
            s.setTag("area", where)
            Sentry.captureException(t)
        }
    }

    /** Context for the next report, e.g. "export started". */
    fun note(category: String, message: String) {
        if (!enabled) return
        Sentry.addBreadcrumb(Breadcrumb().apply { this.category = category; this.message = message; level = SentryLevel.INFO })
    }
}
