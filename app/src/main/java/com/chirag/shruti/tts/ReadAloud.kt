package com.chirag.shruti.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.chirag.shruti.data.LibraryRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a PDF aloud with the phone's text-to-speech voice, page by page,
 * for books where you have the PDF but no audiobook files.
 */
class ReadAloud(private val context: Context, private val repo: LibraryRepository) {
    data class State(
        val bookId: String? = null,
        val page: Int = 0,
        val pageCount: Int = 0,
        val speaking: Boolean = false,
        val preparing: Boolean = false,
        val rate: Float = 1f,
        val message: String? = null,
    ) {
        val active get() = bookId != null
    }

    private val _state = MutableStateFlow(State(rate = repo.readAloudRate))
    val state: StateFlow<State> = _state.asStateFlow()

    var onStarted: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var afterInit: (() -> Unit)? = null

    private var doc: PDDocument? = null
    private var docPath: String? = null
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private var session = 0
    private var blankRun = 0
    private var foundText = false

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            scope.launch { chunkFinished(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            scope.launch { chunkFinished(utteranceId) }
        }
    }

    private fun withTts(block: () -> Unit) {
        if (ready && tts != null) {
            block(); return
        }
        afterInit = block
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            scope.launch {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(listener)
                    afterInit?.invoke()
                    afterInit = null
                } else {
                    tts = null
                    _state.update {
                        State(rate = it.rate, message = "No text-to-speech voice found. Install \"Speech Services by Google\" from the Play Store.")
                    }
                }
            }
        }
    }

    fun start(bookId: String, pdf: File, page: Int) {
        session++
        tts?.stop()
        onStarted?.invoke()
        _state.update { it.copy(bookId = bookId, page = page, speaking = true, preparing = true, message = null) }
        val mySession = session
        scope.launch {
            val count = runCatching {
                withContext(Dispatchers.IO) {
                    val key = "${pdf.path}:${pdf.lastModified()}:${pdf.length()}"
                    if (docPath != key || doc == null) {
                        doc?.close()
                        doc = null
                        doc = PDDocument.load(pdf)
                        docPath = key
                    }
                    doc!!.numberOfPages
                }
            }.getOrElse {
                _state.update { s -> s.copy(speaking = false, preparing = false, message = "Couldn't open this PDF for reading aloud.") }
                return@launch
            }
            if (mySession != session) return@launch
            _state.update { it.copy(pageCount = count) }
            blankRun = 0
            foundText = false
            withTts { loadPage(page, mySession) }
        }
    }

    private fun loadPage(page: Int, mySession: Int) {
        if (mySession != session) return
        val d = doc ?: return
        if (page >= d.numberOfPages) {
            _state.update { it.copy(speaking = false, preparing = false, message = "Finished reading.") }
            return
        }
        _state.update { it.copy(page = page, preparing = true) }
        repo.update(_state.value.bookId ?: return) { it.copy(pdfPage = page) }
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    synchronized(d) {
                        PDFTextStripper().apply {
                            startPage = page + 1
                            endPage = page + 1
                            sortByPosition = true
                        }.getText(d)
                    }
                }.getOrDefault("")
            }
            if (mySession != session) return@launch
            chunks = split(text)
            chunkIndex = 0
            if (chunks.isEmpty()) {
                blankRun++
                if (!foundText && blankRun >= 6) {
                    _state.update {
                        it.copy(speaking = false, preparing = false,
                            message = "This PDF has no readable text (it looks like scanned images), so it can't be read aloud.")
                    }
                    return@launch
                }
                loadPage(page + 1, mySession)
                return@launch
            }
            foundText = true
            blankRun = 0
            _state.update { it.copy(preparing = false) }
            speakCurrent()
        }
    }

    private fun speakCurrent() {
        val t = tts ?: return
        val text = chunks.getOrNull(chunkIndex) ?: return
        t.setSpeechRate(_state.value.rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "$session:${_state.value.page}:$chunkIndex")
    }

    private fun chunkFinished(id: String?) {
        val parts = id?.split(':') ?: return
        if (parts.size != 3 || parts[0].toIntOrNull() != session || !_state.value.speaking) return
        chunkIndex = (parts[2].toIntOrNull() ?: chunkIndex) + 1
        if (chunkIndex < chunks.size) speakCurrent() else loadPage(_state.value.page + 1, session)
    }

    fun pause() {
        if (!_state.value.speaking) return
        session++
        tts?.stop()
        _state.update { it.copy(speaking = false) }
    }

    fun resume() {
        val s = _state.value
        if (!s.active || s.speaking) return
        onStarted?.invoke()
        session++
        _state.update { it.copy(speaking = true, message = null) }
        if (chunks.isEmpty() || chunkIndex >= chunks.size) withTts { loadPage(s.page, session) } else withTts { speakCurrent() }
    }

    fun jumpTo(page: Int) {
        val s = _state.value
        if (!s.active) return
        session++
        tts?.stop()
        _state.update { it.copy(speaking = true, message = null) }
        withTts { loadPage(page.coerceIn(0, (s.pageCount - 1).coerceAtLeast(0)), session) }
    }

    fun setRate(rate: Float) {
        repo.readAloudRate = rate
        _state.update { it.copy(rate = rate) }
        if (_state.value.speaking && chunks.isNotEmpty()) {
            session++
            tts?.stop()
            speakCurrent()
        }
    }

    fun stop() {
        if (!_state.value.active) return
        session++
        tts?.stop()
        chunks = emptyList()
        _state.update { State(rate = it.rate) }
        val d = doc
        doc = null
        docPath = null
        if (d != null) scope.launch(Dispatchers.IO) { runCatching { synchronized(d) { d.close() } } }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        private const val MAX = 280

        fun split(raw: String): List<String> {
            val text = raw
                .replace(Regex("-\\s*\\n\\s*"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isBlank()) return emptyList()
            val sentences = text.split(Regex("(?<=[.!?…”\"])\\s+"))
            val out = mutableListOf<String>()
            val buf = StringBuilder()
            fun flush() {
                if (buf.isNotBlank()) out += buf.toString().trim(); buf.clear()
            }
            for (s in sentences) {
                if (s.length > MAX) {
                    flush()
                    var rest = s
                    while (rest.length > MAX) {
                        var cut = rest.lastIndexOf(", ", MAX).takeIf { it > MAX / 3 } ?: rest.lastIndexOf(' ', MAX)
                        if (cut <= 0) cut = MAX
                        out += rest.substring(0, cut + 1).trim()
                        rest = rest.substring(cut + 1)
                    }
                    buf.append(rest)
                } else {
                    if (buf.length + s.length + 1 > MAX) flush()
                    if (buf.isNotEmpty()) buf.append(' ')
                    buf.append(s)
                }
            }
            flush()
            return out.filter { it.any(Char::isLetterOrDigit) }
        }
    }
}
