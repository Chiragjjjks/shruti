package com.chirag.shruti.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Renders PDF pages to bitmaps with Android's built-in renderer, one page at a time. */
class PdfPages private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** height / width for every page */
    val ratios: FloatArray,
) {
    val pageCount get() = ratios.size
    private val mutex = Mutex()
    private var closed = false
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(index: Int, width: Int): Bitmap? = cache.get("$index@$width")

    suspend fun render(index: Int, width: Int): Bitmap? = mutex.withLock {
        cache.get("$index@$width")?.let { return@withLock it }
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val w = width.coerceAtLeast(1)
                val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put("$index@$width", bmp)
                bmp
            }
        }
    }

    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { renderer.close() }
                runCatching { pfd.close() }
                cache.evictAll()
            }
        }
    }

    companion object {
        fun open(file: File): PdfPages {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                pfd.close(); throw e
            }
            val ratios = try {
                FloatArray(renderer.pageCount) { i ->
                    renderer.openPage(i).use { it.height.toFloat() / it.width.coerceAtLeast(1) }
                }
            } catch (e: Exception) {
                runCatching { renderer.close() }; runCatching { pfd.close() }; throw e
            }
            return PdfPages(pfd, renderer, ratios)
        }
    }
}
