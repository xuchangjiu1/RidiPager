package com.ridipager.app

import android.graphics.Paint
import android.text.TextPaint
import java.io.File

/**
 * 极简分页引擎：
 *  - 按窗口可用宽高 + 字号，把纯文本切成一页页
 *  - 支持中文/英文混排（按字符宽度累加，中文按整宽算）
 *  - 保留段落换行
 *
 * 不依赖任何第三方库，纯 Canvas/Paint 测量。
 */
class Paginator(
    private val contentWidth: Float,
    private val contentHeight: Float,
    private val paint: TextPaint,
    private val lineSpacing: Float = 1.5f
) {
    private val lineHeight: Float = paint.textSize * lineSpacing
    private val linesPerPage: Int = (contentHeight / lineHeight).toInt().coerceAtLeast(1)

    /** 空白行也应占据一整行高度 */
    private val spaceWidth: Float = paint.measureText(" ")

    /**
     * 把全文切成若干页；每页是该页要绘制的文本行列表。
     */
    fun paginate(text: String): List<List<PageLine>> {
        val lines = mutableListOf<PageLine>()
        val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        for (para in paragraphs) {
            if (para.isEmpty()) {
                lines.add(PageLine("", 0f))   // 段间空行
                continue
            }
            // 逐字符排版，遇到宽度超限就换行
            val sb = StringBuilder()
            var w = 0f
            for (ch in para) {
                val cw = if (ch == '\t') spaceWidth * 4 else paint.measureText(ch.toString())
                if (w + cw > contentWidth && sb.isNotEmpty()) {
                    lines.add(PageLine(sb.toString(), w))
                    sb.clear()
                    w = 0f
                }
                sb.append(ch)
                w += cw
            }
            if (sb.isNotEmpty()) lines.add(PageLine(sb.toString(), w))
        }

        // 按每页行数切块
        val pages = mutableListOf<List<PageLine>>()
        var i = 0
        while (i < lines.size) {
            val end = (i + linesPerPage).coerceAtMost(lines.size)
            pages.add(lines.subList(i, end).toList())
            i = end
        }
        if (pages.isEmpty()) pages.add(listOf(PageLine("（空文件）", 0f)))
        return pages
    }

    class PageLine(val text: String, val width: Float)

    companion object {
        /** 从文件读文本，尽量自动识别常见编码（UTF-8 / GBK 之类） */
        fun readText(file: File): String {
            val bytes = file.readBytes()
            return decodeBytes(bytes)
        }

        fun decodeBytes(bytes: ByteArray): String {
            // 1) 尝试 UTF-8（严格）
            try {
                val dec = Charsets.UTF_8.newDecoder()
                dec.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                dec.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                return dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) { }

            // 2) BOM 检测
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }

            // 3) 回退 GBK（中文 TXT 常见）
            return try {
                String(bytes, charset("GBK"))
            } catch (_: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }
}
