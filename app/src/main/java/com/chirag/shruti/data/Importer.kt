package com.chirag.shruti.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Brings the reader's own files (from phone storage, or packed into the APK) into the library. */
object Importer {
    private val audioExt = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "wav", "flac")
    private val imageExt = setOf("jpg", "jpeg", "png", "webp")

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun isAudio(name: String, mime: String? = null) = mime?.startsWith("audio/") == true || ext(name) in audioExt

    val natural = Comparator<String> { a, b ->
        val tok = Regex("\\d+|\\D+")
        val ta = tok.findAll(a.lowercase()).map { it.value }.toList()
        val tb = tok.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]; val y = tb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0'); val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ta.size - tb.size
    }

    fun chapterTitle(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun persist(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun durationOf(context: Context, uri: Uri): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                release()
            }
        }
    }.getOrDefault(0L)

    private fun assetDuration(context: Context, path: String): Long = runCatching {
        context.assets.openFd(path).use { fd ->
            MediaMetadataRetriever().run {
                try {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    release()
                }
            }
        }
    }.getOrDefault(0L)

    private fun copyTo(input: InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true); tmp.delete()
        }
    }

    private fun pdfFile(context: Context, bookId: String) = File(context.filesDir, "pdf/$bookId.pdf")
    private fun coverFile(context: Context, bookId: String) = File(context.filesDir, "covers/$bookId")

    suspend fun importPdf(context: Context, repo: LibraryRepository, bookId: String, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val name = displayName(context, uri)
            val dest = pdfFile(context, bookId)
            copyTo(context.contentResolver.openInputStream(uri) ?: error("Can't open $name"), dest)
            repo.update(bookId) { it.copy(pdfPath = dest.path, pdfName = name, pdfPage = 0) }
            name
        }

    suspend fun importCover(context: Context, repo: LibraryRepository, bookId: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            val dest = coverFile(context, bookId)
            copyTo(context.contentResolver.openInputStream(uri) ?: error("Can't open image"), dest)
            // New path each time so the cover cache refreshes.
            repo.update(bookId) { it.copy(coverPath = dest.path + "#" + System.currentTimeMillis()) }
        }

    suspend fun importAudioFiles(context: Context, repo: LibraryRepository, bookId: String, uris: List<Uri>): Int =
        withContext(Dispatchers.IO) {
            val named = uris.map { persist(context, it); it to displayName(context, it) }
                .sortedWith { a, b -> natural.compare(a.second, b.second) }
            val chapters = named.map { (uri, name) -> Chapter(uri.toString(), chapterTitle(name), durationOf(context, uri)) }
            setChapters(context, repo, bookId, chapters)
            chapters.size
        }

    data class FolderResult(val chapters: Int, val pdf: String?, val cover: Boolean)

    /** A whole book folder: audio files become chapters; a PDF and cover image are picked up too. */
    suspend fun importFolder(context: Context, repo: LibraryRepository, bookId: String, tree: Uri): FolderResult =
        withContext(Dispatchers.IO) {
            persist(context, tree)
            val audio = mutableListOf<Pair<Uri, String>>()
            var pdf: Pair<Uri, String>? = null
            var cover: Uri? = null
            fun walk(docId: String, depth: Int) {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                val cols = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> if (depth < 2) walk(id, depth + 1)
                            isAudio(name, mime) -> audio += uri to (if (depth == 0) name else "$depth/$name")
                            ext(name) == "pdf" && pdf == null -> pdf = uri to name
                            ext(name) in imageExt && cover == null -> cover = uri
                        }
                    }
                }
            }
            walk(DocumentsContract.getTreeDocumentId(tree), 0)
            val chapters = audio.sortedWith { a, b -> natural.compare(a.second, b.second) }
                .map { (uri, name) -> Chapter(uri.toString(), chapterTitle(name.substringAfterLast('/')), durationOf(context, uri)) }
            if (chapters.isNotEmpty()) setChapters(context, repo, bookId, chapters)
            val pdfName = pdf?.let { (uri, _) -> importPdf(context, repo, bookId, uri) }
            cover?.let { importCover(context, repo, bookId, it) }
            FolderResult(chapters.size, pdfName, cover != null)
        }

    private fun setChapters(context: Context, repo: LibraryRepository, bookId: String, chapters: List<Chapter>) {
        repo.update(bookId) { it.copy(chapters = chapters, chapterIndex = 0, positionMs = 0, finished = false) }
        releaseUnusedGrants(context, repo)
    }

    fun removeAudio(context: Context, repo: LibraryRepository, bookId: String) {
        repo.update(bookId) { it.copy(chapters = emptyList(), chapterIndex = 0, positionMs = 0, finished = false) }
        releaseUnusedGrants(context, repo)
    }

    /** Android caps persisted grants per app, so let go of files no book points at any more. */
    private fun releaseUnusedGrants(context: Context, repo: LibraryRepository) {
        val used = repo.books.value.flatMap { b -> b.chapters.map { grantKey(it.uri) } }.toSet()
        val cr = context.contentResolver
        cr.persistedUriPermissions.forEach { perm ->
            if (grantKey(perm.uri.toString()) !in used) {
                runCatching { cr.releasePersistableUriPermission(perm.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
    }

    /** A file picked from a folder is covered by the folder's (tree) grant. */
    private fun grantKey(uri: String): String =
        if ("/tree/" in uri) uri.substringBefore("/document/") else uri

    fun removePdf(context: Context, repo: LibraryRepository, bookId: String) {
        pdfFile(context, bookId).delete()
        repo.update(bookId) { it.copy(pdfPath = null, pdfName = null, pdfPage = 0) }
    }

    /**
     * Files packed into the APK at build time live in assets/books/<id>/:
     *   book.pdf (any .pdf), cover.jpg or .png, and audio files in audio/ (mp3, m4a, m4b…)
     * They fill in whatever the reader hasn't attached themselves.
     */
    suspend fun syncBundled(context: Context, repo: LibraryRepository) = withContext(Dispatchers.IO) {
        val am = context.assets
        for (seed in Catalogue.books) {
            val base = "books/${seed.id}"
            val top = runCatching { am.list(base)?.toList() }.getOrNull().orEmpty()
            if (top.isEmpty()) continue
            val book = repo.book(seed.id) ?: continue
            if (book.pdfPath == null || !File(book.pdfPath).exists()) {
                top.firstOrNull { ext(it) == "pdf" }?.let { name ->
                    val dest = pdfFile(context, seed.id)
                    copyTo(am.open("$base/$name"), dest)
                    repo.update(seed.id) { it.copy(pdfPath = dest.path, pdfName = name) }
                }
            }
            if (book.coverPath == null) {
                top.firstOrNull { ext(it) in imageExt }?.let { name ->
                    val dest = coverFile(context, seed.id)
                    copyTo(am.open("$base/$name"), dest)
                    repo.update(seed.id) { it.copy(coverPath = dest.path) }
                }
            }
            if (book.chapters.isEmpty()) {
                val inAudioDir = runCatching { am.list("$base/audio")?.toList() }.getOrNull().orEmpty()
                    .filter { isAudio(it) }.map { "$base/audio/$it" }
                val atTop = top.filter { isAudio(it) }.map { "$base/$it" }
                val paths = (inAudioDir + atTop).sortedWith { a, b -> natural.compare(a.substringAfterLast('/'), b.substringAfterLast('/')) }
                if (paths.isNotEmpty()) {
                    val chapters = paths.map {
                        Chapter("asset:///$it", chapterTitle(it.substringAfterLast('/')), assetDuration(context, it))
                    }
                    setChapters(context, repo, seed.id, chapters)
                }
            }
        }
    }
}
