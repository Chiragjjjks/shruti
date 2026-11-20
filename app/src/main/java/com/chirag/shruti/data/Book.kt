package com.chirag.shruti.data

enum class CoverArt { IKIGAI, SHIVA }

data class Chapter(
    val uri: String,
    val title: String,
    val durationMs: Long,
)

/**
 * A book in the library. Catalogue fields (title, author…) are fixed; the rest is the
 * reader's own state: files they attached and where they left off.
 */
data class Book(
    val id: String,
    val title: String,
    val subtitle: String,
    val authors: String,
    val narrator: String?,
    val badge: String?,
    val rating: Float?,
    val ratingsLabel: String?,
    val lengthLabel: String?,
    val description: String,
    val art: CoverArt,
    val tint: Long,
    // Reader state
    val pdfPath: String? = null,
    val pdfName: String? = null,
    val coverPath: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val positionMs: Long = 0,
    val pdfPage: Int = 0,
    val lastPlayed: Long = 0,
    val lastRead: Long = 0,
    val finished: Boolean = false,
) {
    val hasAudio get() = chapters.isNotEmpty()
    val hasPdf get() = pdfPath != null
    val totalMs get() = chapters.sumOf { it.durationMs }

    fun listenedMs(chapter: Int = chapterIndex, position: Long = positionMs): Long =
        chapters.take(chapter.coerceIn(0, chapters.size)).sumOf { it.durationMs } + position

    fun progress(chapter: Int = chapterIndex, position: Long = positionMs): Float {
        val total = totalMs
        if (finished) return 1f
        return if (total <= 0) 0f else (listenedMs(chapter, position).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Label for the main listen button. */
    val listenLabel: String
        get() = when {
            finished -> "Listen again"
            progress() > 0f -> "Resume"
            else -> "Listen"
        }

    /** "3h 23m" from the real audio when attached, else the catalogue length. */
    val displayLength: String?
        get() = if (totalMs > 0) formatLength(totalMs) else lengthLabel
}

fun formatLength(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        ms in 1 until 60_000 -> "<1m"
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatLeft(ms: Long): String = if (ms < 60_000) "Less than a minute left" else "${formatLength(ms)} left"

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

object MediaIds {
    fun of(bookId: String, chapter: Int) = "$bookId|$chapter"
    fun parse(id: String): Pair<String, Int>? {
        val i = id.lastIndexOf('|')
        if (i <= 0) return null
        return id.substring(0, i) to (id.substring(i + 1).toIntOrNull() ?: return null)
    }
}
