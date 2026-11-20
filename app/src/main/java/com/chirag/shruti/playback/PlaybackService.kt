package com.chirag.shruti.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chirag.shruti.MainActivity
import com.chirag.shruti.ShrutiApp
import com.chirag.shruti.data.MediaIds

/** Keeps the audiobook playing with the screen off, with lock-screen/notification controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val saver = object : Runnable {
        override fun run() {
            if (session?.player?.isPlaying == true) savePosition()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = savePosition()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePosition()
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) markFinished()
            }
        })
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        handler.post(saver)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacks(saver)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun current(): Pair<String, Int>? =
        session?.player?.currentMediaItem?.mediaId?.let(MediaIds::parse)

    private fun savePosition() {
        val p = session?.player ?: return
        if (p.playbackState == Player.STATE_ENDED) return // markFinished() owns this state
        val (bookId, chapter) = current() ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        ShrutiApp.repo.update(bookId) {
            it.copy(chapterIndex = chapter, positionMs = pos, lastPlayed = System.currentTimeMillis())
        }
    }

    private fun markFinished() {
        val (bookId, _) = current() ?: return
        ShrutiApp.repo.update(bookId) { it.copy(finished = true, chapterIndex = 0, positionMs = 0) }
    }
}
