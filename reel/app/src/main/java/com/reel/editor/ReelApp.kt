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
    }
}
