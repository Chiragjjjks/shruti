package com.chirag.shruti.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Library state: catalogue books merged with what the reader attached, saved in prefs. */
class LibraryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val _books = MutableStateFlow(Catalogue.books.map(::restore))
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    /** Most recently played book that actually has audio. */
    fun lastPlayed(): Book? = _books.value.filter { it.hasAudio }.maxByOrNull { it.lastPlayed }

    fun update(id: String, transform: (Book) -> Book) {
        synchronized(this) {
            var changed: Book? = null
            _books.value = _books.value.map {
                if (it.id == id) transform(it).also { b -> changed = b } else it
            }
            changed?.let(::save)
        }
    }

    var playbackSpeed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) = prefs.edit().putFloat("speed", value).apply()

    var readAloudRate: Float
        get() = prefs.getFloat("tts_rate", 1f)
        set(value) = prefs.edit().putFloat("tts_rate", value).apply()

    private fun save(b: Book) {
        val o = JSONObject()
            .put("pdfPath", b.pdfPath)
            .put("pdfName", b.pdfName)
            .put("coverPath", b.coverPath)
            .put("chapterIndex", b.chapterIndex)
            .put("positionMs", b.positionMs)
            .put("pdfPage", b.pdfPage)
            .put("lastPlayed", b.lastPlayed)
            .put("lastRead", b.lastRead)
            .put("finished", b.finished)
        val arr = JSONArray()
        b.chapters.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("title", it.title).put("durationMs", it.durationMs))
        }
        o.put("chapters", arr)
        prefs.edit().putString("book_${b.id}", o.toString()).apply()
    }

    private fun restore(seed: Book): Book {
        val raw = prefs.getString("book_${seed.id}", null) ?: return seed
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("chapters") ?: JSONArray()
            val chapters = (0 until arr.length()).map {
                val c = arr.getJSONObject(it)
                Chapter(c.getString("uri"), c.optString("title"), c.optLong("durationMs"))
            }
            seed.copy(
                pdfPath = o.optString("pdfPath").ifEmpty { null },
                pdfName = o.optString("pdfName").ifEmpty { null },
                coverPath = o.optString("coverPath").ifEmpty { null },
                chapters = chapters,
                chapterIndex = o.optInt("chapterIndex"),
                positionMs = o.optLong("positionMs"),
                pdfPage = o.optInt("pdfPage"),
                lastPlayed = o.optLong("lastPlayed"),
                lastRead = o.optLong("lastRead"),
                finished = o.optBoolean("finished"),
            )
        }.getOrDefault(seed)
    }
}
