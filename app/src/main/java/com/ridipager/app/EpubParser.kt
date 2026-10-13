package com.ridipager.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * 纯手写 EPUB 解析器（零第三方依赖）。
 *
 * EPUB 本质是一个 ZIP：
 *   META-INF/container.xml   →  指向 .opf 文件
 *   *.opf                    →  书单：manifest（文件清单）+ spine（阅读顺序）
 *   *.xhtml / *.html         →  正文内容
 *
 * 解析流程：
 *   1. 读 container.xml 找到 opf 路径
 *   2. 解析 opf，取 manifest 的 id→href 映射 和 spine 的 idref 顺序
 *   3. 按 spine 顺序读每个 xhtml，剥掉标签得到纯文本
 */
object EpubParser {

    /** 解析结果：一本书的各章节 */
    data class Book(
        val title: String,
        val chapters: List<Chapter>
    )

    data class Chapter(
        val title: String,
        val text: String
    )

    /**
     * 解析 EPUB 文件。
     * @throws IllegalArgumentException 不是合法 EPUB 时抛出
     */
    fun parse(file: File): Book {
        ZipFile(file).use { zip ->
            // ---------- 1. 定位 OPF ----------
            val opfPath = findOpfPath(zip)
                ?: throw IllegalArgumentException("非法 EPUB：找不到 container.xml 或 OPF")

            val opfDir = opfPath.substringBeforeLast('/', "")

            // ---------- 2. 解析 OPF ----------
            val manifest = mutableMapOf<String, String>()   // id -> href
            val spine = mutableListOf<String>()             // idref 顺序
            var bookTitle = file.nameWithoutExtension

            zip.getInputStream(zip.getEntry(opfPath)!!).use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, null)
                var event = parser.eventType
                var currentId: String? = null
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name.lowercase()) {
                                "dc:title", "title" -> {
                                    if (bookTitle == file.nameWithoutExtension) {
                                        val t = readTextOf(parser)
                                        if (t.isNotBlank()) bookTitle = t.trim()
                                    }
                                }
                                "item" -> {
                                    val id = parser.getAttributeValue(null, "id")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (id != null && href != null) manifest[id] = href
                                }
                                "itemref" -> {
                                    val idref = parser.getAttributeValue(null, "idref")
                                    if (idref != null) spine.add(idref)
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            if (manifest.isEmpty()) throw IllegalArgumentException("非法 EPUB：manifest 为空")

            // ---------- 3. 按 spine 顺序提取正文 ----------
            val order = if (spine.isNotEmpty()) spine
            else manifest.keys.toList()   // 没有 spine 就按 manifest 全部来

            val chapters = ArrayList<Chapter>(order.size)
            for (idref in order) {
                val href = manifest[idref] ?: continue
                val entryPath = resolvePath(opfDir, href)
                val entry = zip.getEntry(entryPath)
                    ?: zip.getEntry(decodeUrl(entryPath))
                    ?: continue
                val raw = zip.getInputStream(entry).use { readAll(it) }
                val text = extractText(raw, entryPath)
                if (text.isNotBlank()) {
                    val chTitle = guessChapterTitle(text) ?: "第 ${chapters.size + 1} 章"
                    chapters.add(Chapter(chTitle, text))
                }
            }

            if (chapters.isEmpty()) throw IllegalArgumentException("EPUB 内容为空或格式不支持")
            return Book(bookTitle, chapters)
        }
    }

    // ==================== 内部工具 ====================

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(container).use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name.lowercase().endsWith("rootfile")
                ) {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (fullPath != null) return fullPath
                }
                event = parser.next()
            }
        }
        // 兜底：直接找 .opf
        return zip.entries().asSequence()
            .firstOrNull { it.name.endsWith(".opf", true) }?.name
    }

    /** 拼接相对路径，处理 ../ */
    private fun resolvePath(baseDir: String, href: String): String {
        val clean = decodeUrl(href)
        if (baseDir.isEmpty()) return clean
        val parts = (baseDir + "/" + clean).split('/')
        val stack = mutableListOf<String>()
        for (p in parts) {
            when (p) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun decodeUrl(s: String): String =
        try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun readAll(input: InputStream): String {
        val bytes = input.readBytes()
        return Paginator.decodeBytes(bytes)
    }

    /** 从 XHTML 中抽取纯文本 */
    private fun extractText(raw: String, path: String): String {
        val isHtml = path.endsWith(".html", true) || path.endsWith(".xhtml", true) ||
                path.endsWith(".htm", true)
        if (!isHtml) return raw.trim()

        // 去掉脚本/样式
        var s = raw
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "")

        // 块级标签 → 换行
        s = s.replace(Regex("(?i)<\\s*(br|/p|/div|/h[1-6]|/li|/tr)\\s*/?>"), "\n")
        // 其余标签全部剥离
        s = s.replace(Regex("(?s)<[^>]+>"), "")

        // HTML 实体解码
        s = decodeEntities(s)

        // 压缩多余空行
        s = s.replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .lines().joinToString("\n") { it.trim() }
            .trim()

        return s
    }

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val map = mapOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
            "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
            "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
            "&ldquo;" to "\u201C", "&rdquo;" to "\u201D",
            "&lsquo;" to "\u2018", "&rsquo;" to "\u2019"
        )
        var out = s
        for ((k, v) in map) out = out.replace(k, v)
        // 数字实体 &#123; / &#x1F;
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) {
            try { String(Character.toChars(it.groupValues[1].toInt(16))) } catch (_: Exception) { "" }
        }
        out = Regex("&#(\\d+);").replace(out) {
            try { String(Character.toChars(it.groupValues[1].toInt())) } catch (_: Exception) { "" }
        }
        return out
    }

    /** 尝试从正文开头猜章节标题 */
    private fun guessChapterTitle(text: String): String? {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val t = firstLine.trim()
        return if (t.length in 1..40) t else null
    }

    /** 读取当前标签内的文本（用于 dc:title） */
    private fun readTextOf(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> sb.append(parser.text)
            }
        }
        return sb.toString()
    }
}
