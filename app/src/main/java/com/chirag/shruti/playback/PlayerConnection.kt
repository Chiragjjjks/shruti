package com.chirag.shruti.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chirag.shruti.data.Book
import com.chirag.shruti.data.LibraryRepository
import com.chirag.shruti.data.MediaIds
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val bookId: String? = null,
    val chapterIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sleepEndsAt: Long? = null,
    val sleepAtChapterEnd: Boolean = false,
    val error: String? = null,
)

/** UI-side handle on the playback service. */
class PlayerConnection(private val context: Context, private val repo: LibraryRepository) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val scope = MainScope()
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState(speed = repo.playbackSpeed))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    var onStarted: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Started from the notification, lock screen or headset: silence read-aloud too.
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) onStarted?.invoke()
            refresh()
            if (player.isPlaying) startTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepAtChapterEnd) {
                controller?.pause()
                _state.update { it.copy(sleepAtChapterEnd = false) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(error = "Couldn't play this file. If you moved or deleted it, attach the audio again.")
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerConnection.controller === controller) {
                        this@PlayerConnection.controller = null
                        future = null
                    }
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null; pending.clear(); return@addListener
            }
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (controller?.isPlaying == true) {
                refresh()
                delay(500)
            }
            refresh()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val parsed = c.currentMediaItem?.mediaId?.let(MediaIds::parse)
        _state.update {
            it.copy(
                bookId = parsed?.first,
                chapterIndex = parsed?.second ?: 0,
                isPlaying = c.isPlaying,
                playWhenReady = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: 0,
                speed = c.playbackParameters.speed,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Play a book: resumes where you left off, or jumps to [chapter]/[positionMs] if given. */
    fun play(book: Book, chapter: Int? = null, positionMs: Long? = null) {
        if (!book.hasAudio) return
        onStarted?.invoke()
        if (book.finished) repo.update(book.id) { it.copy(finished = false, chapterIndex = 0, positionMs = 0) }
        withController { c ->
            val loaded = c.currentMediaItem?.mediaId?.let(MediaIds::parse)?.first == book.id &&
                c.mediaItemCount == book.chapters.size
            if (loaded && chapter == null) {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
                c.play()
                return@withController
            }
            if (loaded && chapter != null) {
                c.seekTo(chapter, positionMs ?: 0)
                c.play()
                return@withController
            }
            // Switching books: keep the outgoing book's exact place.
            c.currentMediaItem?.mediaId?.let(MediaIds::parse)?.let { (oldId, oldChapter) ->
                if (oldId != book.id) {
                    val pos = c.currentPosition.coerceAtLeast(0)
                    repo.update(oldId) { it.copy(chapterIndex = oldChapter, positionMs = pos) }
                }
            }
            val artwork = book.coverPath?.let { Uri.fromFile(File(it.substringBefore('#'))) }
            val items = book.chapters.mapIndexed { i, ch ->
                MediaItem.Builder()
                    .setMediaId(MediaIds.of(book.id, i))
                    .setUri(ch.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(ch.title)
                            .setArtist(book.authors)
                            .setAlbumTitle(book.title)
                            .setArtworkUri(artwork)
                            .build(),
                    )
                    .build()
            }
            val startChapter = (chapter ?: if (book.finished) 0 else book.chapterIndex).coerceIn(0, items.lastIndex)
            val startPos = positionMs ?: if (chapter == null && !book.finished) book.positionMs else 0L
            c.setMediaItems(items, startChapter, startPos)
            c.setPlaybackSpeed(repo.playbackSpeed)
            c.prepare()
            c.play()
        }
    }

    fun toggle(fallback: Book?) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            fallback?.let { play(it) }; return
        }
        if (c.playWhenReady) c.pause() else {
            onStarted?.invoke()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    /** Drop a book's files from the player before they're replaced or removed. */
    fun unload(bookId: String) {
        val c = controller ?: return
        if (c.currentMediaItem?.mediaId?.let(MediaIds::parse)?.first == bookId) {
            c.stop()
            c.clearMediaItems()
            refresh()
        }
    }

    fun seekBy(deltaMs: Long) = withController { c ->
        val target = c.currentPosition + deltaMs
        when {
            target < 0 && c.hasPreviousMediaItem() -> {
                // Step back into the previous chapter.
                val prev = c.currentMediaItemIndex - 1
                val bookId = c.currentMediaItem?.mediaId?.let(MediaIds::parse)?.first
                val known = bookId?.let { repo.book(it) }?.chapters?.getOrNull(prev)?.durationMs ?: 0L
                if (known > 0) {
                    c.seekTo(prev, (known + target).coerceAtLeast(0))
                } else {
                    c.seekTo(prev, C.TIME_UNSET)
                    scope.launch {
                        repeat(20) {
                            delay(150)
                            val d = c.duration
                            if (d != C.TIME_UNSET) { c.seekTo(prev, (d + target).coerceAtLeast(0)); return@launch }
                        }
                    }
                }
            }
            c.duration != C.TIME_UNSET && target > c.duration && c.hasNextMediaItem() -> c.seekToNextMediaItem()
            else -> c.seekTo(target.coerceAtLeast(0))
        }
        refresh()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs); refresh() }
    fun nextChapter() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem(); refresh() }
    fun previousChapter() = withController {
        if (it.currentPosition > 5_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem()
        refresh()
    }

    fun setSpeed(speed: Float) {
        repo.playbackSpeed = speed
        _state.update { it.copy(speed = speed) }
        controller?.setPlaybackSpeed(speed)
    }

    /** Sleep timer: minutes, or null to cancel. */
    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepEndsAt = null, sleepAtChapterEnd = false) }
        if (minutes == null) return
        val ms = minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = System.currentTimeMillis() + ms) }
        sleepJob = scope.launch {
            delay(ms)
            controller?.pause()
            _state.update { it.copy(sleepEndsAt = null) }
        }
    }

    fun sleepAtEndOfChapter() {
        sleepJob?.cancel()
        _state.update { it.copy(sleepEndsAt = null, sleepAtChapterEnd = true) }
    }
}
