package com.ridipager.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

/**
 * 阅读视图，支持三种内容模式：
 *   1. TEXT —— 纯文本按屏幕分页（TXT）
 *   2. EPUB —— 按章节，章内再分页
 *   3. PDF  —— 逐页渲染 Bitmap
 *
 * 对外统一暴露 nextPage() / prevPage()，翻页器按键只需调这两个方法。
 */
class ReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Mode { NONE, TEXT, EPUB, PDF }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
        isSubpixelText = true
    }

    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 30f
    }
    private val chapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        textSize = 32f
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val padH = 48f
    private val padTop = 56f
    private val padBottom = 72f
    private val lineSpacing = 1.5f

    private var mode = Mode.NONE

    // ---- TEXT / EPUB 共用分页数据 ----
    private var pages: List<List<Paginator.PageLine>> = emptyList()
    private var currentPage = 0

    // ---- EPUB 章节 ----
    private var book: EpubParser.Book? = null
    private var chapterIndex = 0

    // ---- PDF ----
    private var pdf: PdfDocument? = null
    private var pdfPage = 0

    private var statusText = "请先打开一本电子书（TXT / EPUB / PDF）"

    var onPageChanged: ((Int, Int) -> Unit)? = null

    // ==================== 打开文件（统一入口） ====================

    /**
     * 打开文件：**自动在后台线程解析**，解析完回主线程应用。
     * 调用方（主线程）可直接调用，不会 ANR。
     *
     * @param listener 在主线程回调 (成功?, 提示文本)
     */
    fun prepareInBackground(path: String, listener: (Boolean, String) -> Unit) {
        val f = java.io.File(path)
        if (!f.exists()) { postResult(listener, false, "文件不存在"); return }
        val ext = f.extension.lowercase()

        // ---- 后台线程：纯计算（解码 / 解压） ----
        Thread {
            val prepared: Any?
            val kind: Mode
            try {
                when (ext) {
                    "epub" -> {
                        kind = Mode.EPUB
                        prepared = EpubParser.parse(f)
                    }
                    "pdf" -> {
                        if (!PdfDocument.isSupported()) {
                            postResult(listener, false, "PDF 需要 Android 5.0 及以上系统")
                            return@Thread
                        }
                        // PdfRenderer 内部有独立渲染线程，这里只传文件路径
                        kind = Mode.PDF
                        prepared = f
                    }
                    else -> {
                        kind = Mode.TEXT
                        prepared = Paginator.readText(f)
                    }
                }
            } catch (e: Exception) {
                postResult(listener, false, "解析失败：${e.message}")
                return@Thread
            }

            // ---- 回主线程：应用到视图 ----
            post {
                try {
                    when (kind) {
                        Mode.EPUB -> {
                            val b = prepared as EpubParser.Book
                            closePdf()
                            book = b
                            mode = Mode.EPUB
                            chapterIndex = 0
                            loadChapter(0)
                            listener(true, "已加载 EPUB：${b.title}（${b.chapters.size} 章）")
                        }
                        Mode.TEXT -> {
                            val text = prepared as String
                            closePdf()
                            book = null
                            mode = Mode.TEXT
                            currentPage = 0
                            rebuild(text)
                            invalidate()
                            notifyPage()
                            listener(true, "已加载 TXT：${f.name}")
                        }
                        Mode.PDF -> {
                            closePdf()
                            pdf = PdfDocument(context, f)
                            mode = Mode.PDF
                            pdfPage = 0
                            book = null
                            pages = emptyList()
                            invalidate()
                            notifyPage()
                            listener(true, "已加载 PDF：${f.name}（${pdf!!.pageCount} 页）")
                        }
                        else -> listener(false, "不支持的格式")
                    }
                } catch (e: Exception) {
                    listener(false, "加载失败：${e.message}")
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun postResult(listener: (Boolean, String) -> Unit, ok: Boolean, msg: String) {
        post { listener(ok, msg) }
    }

    /** 兼容旧签名：直接委托给异步版本 */
    fun openFile(path: String, listener: ((Boolean, String) -> Unit)? = null) {
        prepareInBackground(path) { ok, msg -> listener?.invoke(ok, msg) }
    }

    private fun closePdf() {
        try { pdf?.close() } catch (_: Exception) {}
        pdf = null
    }

    // ==================== EPUB 章节 ====================

    private fun loadChapter(index: Int) {
        val b = book ?: return
        if (index < 0 || index >= b.chapters.size) return
        chapterIndex = index
        currentPage = 0
        rawText = b.chapters[index].text
        rebuild(rawText)
        invalidate()
        notifyPage()
    }

    // ==================== 文本分页 ====================

    private var rawText: String = ""

    private fun rebuild(text: String) {
        rawText = text
        val cw = width - padH * 2
        val ch = height - padTop - padBottom
        if (cw <= 0f || ch <= 0f) return
        val p = Paginator(cw, ch, textPaint, lineSpacing)
        pages = p.paginate(text)
        if (currentPage >= pages.size) currentPage = (pages.size - 1).coerceAtLeast(0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        if (mode == Mode.TEXT || mode == Mode.EPUB) {
            if (rawText.isNotEmpty()) {
                val old = currentPage
                rebuild(rawText)
                currentPage = old.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                invalidate()
                notifyPage()
            }
        } else if (mode == Mode.PDF) {
            invalidate()   // PDF 按新宽度重新渲染
        }
    }

    // ==================== 绘制 ====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            Mode.PDF -> drawPdf(canvas)
            Mode.TEXT, Mode.EPUB -> drawText(canvas)
            Mode.NONE -> canvas.drawText(statusText, padH, padTop + textPaint.textSize, footerPaint)
        }
    }

    private fun drawText(canvas: Canvas) {
        if (pages.isEmpty()) {
            canvas.drawText(statusText, padH, padTop + textPaint.textSize, footerPaint)
            return
        }
        // EPUB 顶部显示章节名
        var startY = padTop + textPaint.textSize
        if (mode == Mode.EPUB) {
            val b = book
            if (b != null && chapterIndex < b.chapters.size) {
                canvas.drawText(b.chapters[chapterIndex].title, padH, padTop, chapPaint)
            }
        }
        val page = pages.getOrNull(currentPage) ?: return
        val lh = textPaint.textSize * lineSpacing
        var y = startY + lh
        for (line in page) {
            if (line.text.isNotEmpty()) canvas.drawText(line.text, padH, y, textPaint)
            y += lh
        }
        // 页脚
        val footer = footerText()
        val fw = footerPaint.measureText(footer)
        canvas.drawText(footer, (width - fw) / 2f, height - 24f, footerPaint)
    }

    private fun drawPdf(canvas: Canvas) {
        val doc = pdf ?: run {
            canvas.drawText(statusText, padH, padTop + textPaint.textSize, footerPaint)
            return
        }

        // 先看缓存，有就直接画（翻页流畅的关键）
        val cached = doc.peekCache(pdfPage)
        if (cached != null && !cached.isRecycled) {
            drawPdfBitmap(canvas, cached)
            drawPdfFooter(canvas, doc)
            return
        }

        // 没缓存 → 触发异步渲染，先画占位
        canvas.drawText("正在渲染第 ${pdfPage + 1} 页…", padH, padTop + textPaint.textSize, footerPaint)
        val pageToRender = pdfPage
        doc.renderPageAsync(pageToRender, width) { bmp ->
            // 回调已在主线程；只在页码没变时刷新，避免快速翻页时错位
            if (pageToRender == pdfPage && bmp != null && !bmp.isRecycled) {
                invalidate()
            }
        }
        drawPdfFooter(canvas, doc)
    }

    private fun drawPdfBitmap(canvas: Canvas, bmp: Bitmap) {
        val availW = width.toFloat()
        val availH = (height - padBottom).toFloat()
        val scale = minOf(availW / bmp.width, availH / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f - 12f
        val src = Rect(0, 0, bmp.width, bmp.height)
        val dst = RectF(left, top, left + dw, top + dh)
        canvas.drawBitmap(bmp, src, dst, bitmapPaint)
    }

    private fun drawPdfFooter(canvas: Canvas, doc: PdfDocument) {
        val footer = "${pdfPage + 1} / ${doc.pageCount}"
        val fw = footerPaint.measureText(footer)
        canvas.drawText(footer, (width - fw) / 2f, height - 24f, footerPaint)
    }

    private fun footerText(): String {
        return if (mode == Mode.EPUB) {
            val b = book
            "${currentPage + 1} / ${pages.size}   ·   第 ${chapterIndex + 1}/${b?.chapters?.size ?: 1} 章"
        } else {
            "${currentPage + 1} / ${pages.size}"
        }
    }

    // ==================== 翻页（统一接口） ====================

    /** @return true=成功翻页；false=已到边界 */
    fun nextPage(): Boolean {
        when (mode) {
            Mode.PDF -> {
                val doc = pdf ?: return false
                return if (pdfPage < doc.pageCount - 1) {
                    pdfPage++; invalidate(); notifyPage(); true
                } else false
            }
            Mode.EPUB -> {
                if (currentPage < pages.size - 1) {
                    currentPage++; invalidate(); notifyPage(); true
                } else if (chapterIndex < (book?.chapters?.size ?: 1) - 1) {
                    loadChapter(chapterIndex + 1); true      // 自动进入下一章
                } else false
            }
            Mode.TEXT -> {
                if (currentPage < pages.size - 1) {
                    currentPage++; invalidate(); notifyPage(); true
                } else false
            }
            else -> return false
        }
    }

    fun prevPage(): Boolean {
        when (mode) {
            Mode.PDF -> {
                return if (pdfPage > 0) {
                    pdfPage--; invalidate(); notifyPage(); true
                } else false
            }
            Mode.EPUB -> {
                if (currentPage > 0) {
                    currentPage--; invalidate(); notifyPage(); true
                } else if (chapterIndex > 0) {
                    loadChapter(chapterIndex - 1)
                    currentPage = (pages.size - 1).coerceAtLeast(0)   // 跳到上一章末尾
                    invalidate(); notifyPage(); true
                } else false
            }
            Mode.TEXT -> {
                if (currentPage > 0) {
                    currentPage--; invalidate(); notifyPage(); true
                } else false
            }
            else -> return false
        }
    }

    // ==================== 状态查询 ====================

    fun hasContent(): Boolean = when (mode) {
        Mode.PDF -> pdf != null
        Mode.TEXT, Mode.EPUB -> pages.isNotEmpty()
        else -> false
    }

    fun pageInfo(): Pair<Int, Int> = when (mode) {
        Mode.PDF -> Pair(pdfPage + 1, pdf?.pageCount ?: 0)
        Mode.EPUB, Mode.TEXT -> Pair(currentPage + 1, pages.size)
        else -> Pair(0, 0)
    }

    fun totalPages(): Int = pageInfo().second
    fun currentMode(): Mode = mode
    fun chapterInfo(): Pair<Int, Int>? =
        if (mode == Mode.EPUB) Pair(chapterIndex + 1, book?.chapters?.size ?: 0) else null

    private fun notifyPage() {
        onPageChanged?.invoke(pageInfo().first, pageInfo().second)
    }

    fun close() {
        closePdf()
        pages = emptyList()
        book = null
        mode = Mode.NONE
    }
}
