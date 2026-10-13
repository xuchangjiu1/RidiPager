package com.ridipager.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * PDF 支持：用系统内置 PdfRenderer（API 21+）把每页渲染成 Bitmap。
 *
 * 重要约束（Android 的坑）：
 *   PdfRenderer 不是线程安全的，且「同一时刻只能打开一个 Page」。
 *   因此本类内部维护一条**专用后台 HandlerThread**，所有渲染都串行执行，
 *   渲染完成后把 Bitmap 通过回调交回主线程绘制。
 *
 * 缓存：LruCache 缓存最近几页，避免来回翻页重复渲染。
 */
class PdfDocument(context: Context, file: File, private val cacheSize: Int = 6) : AutoCloseable {

    private val renderer: PdfRenderer
    private val pfd: ParcelFileDescriptor

    /** 专用渲染线程，保证 PdfRenderer 串行访问 */
    private val renderThread = HandlerThread("pdf-render").also { it.start() }
    private val renderHandler = Handler(renderThread.looper)

    @Volatile private var closed = false

    private val cache = object : android.util.LruCache<Int, Bitmap>(cacheSize) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    val pageCount: Int

    init {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            "PDF 需要 Android 5.0 及以上"
        }
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        pageCount = renderer.pageCount
    }

    /**
     * 异步渲染第 index 页。
     * @param targetWidth 目标宽度（像素），用于控制内存；实际按比例缩放
     * @param onReady 主线程回调；失败时为 null
     */
    fun renderPageAsync(index: Int, targetWidth: Int, onReady: (Bitmap?) -> Unit) {
        if (closed || index < 0 || index >= pageCount) {
            postMain { onReady(null) }
            return
        }
        // 命中缓存直接回
        cache.get(index)?.let { bmp ->
            postMain { onReady(bmp) }
            return
        }
        renderHandler.post {
            if (closed) { postMain { onReady(null) }; return@post }
            val bmp = try {
                renderer.openPage(index).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val w = targetWidth.coerceAtLeast(1)
                    val h = (w * ratio).toInt().coerceAtLeast(1)
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                }
            } catch (_: Exception) {
                null
            }
            if (bmp != null && !closed) cache.put(index, bmp)
            postMain { onReady(bmp) }
        }
    }

    /** 同步取缓存（不清除），供绘制时快速命中 */
    fun peekCache(index: Int): Bitmap? = cache.get(index)

    fun pageAspect(index: Int): Float {
        if (closed || index < 0 || index >= pageCount) return 1.4f
        return try {
            renderer.openPage(index).use { p -> p.height.toFloat() / p.width.toFloat() }
        } catch (_: Exception) { 1.4f }
    }

    private fun postMain(block: () -> Unit) {
        Handler(android.os.Looper.getMainLooper()).post(block)
    }

    override fun close() {
        if (closed) return
        closed = true
        try { renderHandler.post {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        } } catch (_: Exception) {}
        try { renderThread.quitSafely() } catch (_: Exception) {}
        cache.evictAll()
    }

    companion object {
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
}
