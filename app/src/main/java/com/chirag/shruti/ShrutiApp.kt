package com.chirag.shruti

import android.app.Application
import com.chirag.shruti.data.Importer
import com.chirag.shruti.data.LibraryRepository
import com.chirag.shruti.playback.PlayerConnection
import com.chirag.shruti.tts.ReadAloud
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShrutiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        repo = LibraryRepository(this)
        player = PlayerConnection(this, repo)
        readAloud = ReadAloud(this, repo)
        // The two never talk over each other.
        readAloud.onStarted = { player.pause() }
        player.onStarted = { readAloud.stop() }
        appScope.launch { runCatching { Importer.syncBundled(this@ShrutiApp, repo) } }
    }

    companion object {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var repo: LibraryRepository
            private set
        lateinit var player: PlayerConnection
            private set
        lateinit var readAloud: ReadAloud
            private set
    }
}
