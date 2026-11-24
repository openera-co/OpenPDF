package com.reel.editor

import android.app.Application
import com.reel.editor.edit.ProjectStore
import com.reel.editor.media.AudioAnalyzer
import com.reel.editor.media.Thumbnails

class ReelApp : Application() {
    lateinit var store: ProjectStore
        private set
    lateinit var thumbnails: Thumbnails
        private set
    lateinit var audio: AudioAnalyzer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReports.start(this)
        com.reel.editor.fx.UserLuts.init(this)
        com.reel.editor.fx.Fonts.init(this)
        store = ProjectStore(this)
        thumbnails = Thumbnails(this)
        audio = AudioAnalyzer(this)
        // The shader catalog is about 1 MB of JSON. Load it now, off the main thread, so opening the
        // editor (the timeline asks for it while composing) does not stall the UI.
        Thread({ runCatching { com.reel.editor.fx.FxCatalog.get(this) } }, "fx-catalog").apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }
}
