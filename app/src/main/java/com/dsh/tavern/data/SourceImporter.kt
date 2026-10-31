package com.dsh.tavern.data

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** 导入的小说/同人原文（按章节切分）。 */
data class SourceDocument(
    val title: String,
    val chapters: List<String>,
    val format: String
) {
    val charCount: Int get() = chapters.sumOf { it.length }
}

/** 送给 AI 的原文节选（已按用户要求做关键词定位）。 */
data class SourceExcerpt(
    val text: String,
    val keywords: List<String>
)

/** TXT / EPUB 文本解析（本地，不联网）。 */
object SourceImporter {

    fun parse(bytes: ByteArray, fileName: String): SourceDocument {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".epub") -> parseEpub(bytes, fileName)
            lower.endsWith(".txt") -> parseTxt(bytes, fileName)
            isZip(bytes) -> runCatching { parseEpub(bytes, fileName) }
                .getOrElse { parseTxt(bytes, fileName) }
            else -> parseTxt(bytes, fileName)
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun parseTxt(bytes: ByteArray, fileName: String): SourceDocument {
        val text = decodeText(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (text.isEmpty()) throw IllegalArgumentException("文本为空")
        val title = fileName.substringBeforeLast('.')
        return SourceDocument(title, splitChapters(text), "txt")
    }

    private fun decodeText(bytes: ByteArray): String {
        val data = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(data)).toString()
        } catch (e: Exception) {
            runCatching { String(data, Charset.forName("GBK")) }
                .getOrElse { String(data, Charsets.UTF_8) }
        }
    }

    /** 用「第X章/节/回/卷」标题切分；没有明显章节就整篇作为一章。 */
    fun splitChapters(text: String): List<String> {
        // 兼容「第1章」「第1 章」「第 1 章」等写法（下载器导出的 txt 常见数字与“章”之间有空格）
        val heading = Regex(
            "(?m)^\\s*(第\\s*[0-9零一二三四五六七八九十百千万两]+\\s*[章节回卷][^\\n]{0,30})\\s*$"
        )
        var matches = heading.findAll(text).toList()
        if (matches.size < 2) {
            // 标题与正文同一行时，只按行首匹配切
            val inline = Regex("(?m)^\\s*(第\\s*[0-9零一二三四五六七八九十百千万两]+\\s*[章节回卷])")
            matches = inline.findAll(text).toList()
        }
        if (matches.size < 2) return listOf(text)
        val chapters = mutableListOf<String>()
        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotEmpty()) chapters.add(chunk)
        }
        return if (chapters.isEmpty()) listOf(text) else chapters
    }

    /** 在全文里截取 from→to 两个关键词之间的部分（找不到就退化为全文）；过长时按采样截取。 */
    fun excerptBetween(text: String, fromKeyword: String, toKeyword: String, maxChars: Int): String {
        if (text.isBlank()) return ""
        val from = fromKeyword.trim()
        val to = toKeyword.trim()
        val start = if (from.isBlank()) 0 else text.indexOf(from).takeIf { it >= 0 } ?: 0
        val end = when {
            to.isBlank() -> text.length
            else -> text.indexOf(to, start).takeIf { it >= 0 }?.plus(to.length) ?: text.length
        }
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        val slice = text.substring(safeStart, safeEnd)
        return if (slice.length <= maxChars) {
            slice
        } else {
            sampleForCompression(slice, maxChars).take(maxChars + 500)
        }
    }

    /** 第 from~to 章（1 起算，写反了自动交换）；过长时按采样截取。 */
    fun excerptChapters(chapters: List<String>, from: Int, to: Int, maxChars: Int): String {
        if (chapters.isEmpty()) return ""
        val lower = minOf(from, to).coerceAtLeast(1)
        val upper = maxOf(from, to).coerceAtLeast(1)
        val start = (lower - 1).coerceAtMost(chapters.lastIndex)
        val end = upper.coerceIn(lower, chapters.size)
        val slice = chapters.subList(start, end).joinToString("\n")
        return if (slice.length <= maxChars) {
            slice
        } else {
            sampleForCompression(slice, maxChars).take(maxChars + 500)
        }
    }

    /**
     * 快速压缩模式：不逐字发送全文，按时间顺序在全文里均匀采样若干窗口，
     * 再交给 API 压缩成「重点保留」资料（人物关系/外貌/性格/事件/说话方式）。
     */
    fun sampleForCompression(text: String, maxChars: Int, windows: Int = 24): String {
        if (text.isBlank()) return ""
        if (text.length <= maxChars) return text
        val count = windows.coerceIn(6, 60)
        val window = (maxChars / count).coerceAtLeast(300)
        val step = text.length / count
        val builder = StringBuilder()
        val head = text.take(800)
        builder.append("【开头】\n").append(head).append("\n\n")
        for (index in 0 until count) {
            val rawStart = index * step + (step - window) / 2
            val start = rawStart.coerceIn(0, (text.length - 1).coerceAtLeast(0))
            val end = minOf(text.length, start + window)
            val piece = text.substring(start, end)
            val cleaned = piece.substringAfter('\n', piece).substringBeforeLast('\n', piece).trim()
            if (cleaned.isNotBlank()) {
                builder.append("【采样 ").append(index + 1).append('/').append(count).append("】\n")
                builder.append(cleaned).append("\n\n")
            }
        }
        builder.append("【结尾】\n").append(text.takeLast(800)).append('\n')
        return builder.toString().take(maxChars + 4_000)
    }

    fun parseEpub(bytes: ByteArray, fileName: String): SourceDocument {
        val entries = readZip(bytes)
        val container = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("不是有效 EPUB：缺少 container.xml")
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("EPUB 缺少 OPF 路径")
        val opf = entries[opfPath]?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("读不到 OPF 文件")
        val baseDir = opfPath.substringBeforeLast('/', "")

        val manifest = mutableMapOf<String, String>()
        Regex("<item\\b[^>]*>").findAll(opf).forEach { match ->
            val tag = match.value
            val id = Regex("\\bid=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: return@forEach
            val href = Regex("\\bhref=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: return@forEach
            manifest[id] = resolve(baseDir, href)
        }
        val spineIds = Regex("<itemref\\b[^>]*\\bidref=\"([^\"]+)\"").findAll(opf)
            .map { it.groupValues[1] }
            .toList()

        val chapters = mutableListOf<String>()
        spineIds.forEach { id ->
            val path = manifest[id] ?: return@forEach
            val html = entries[path]?.toString(Charsets.UTF_8) ?: return@forEach
            val text = stripHtml(html).trim()
            if (text.isNotEmpty()) chapters.add(text)
        }
        if (chapters.isEmpty()) {
            entries
                .filterKeys { it.endsWith(".html") || it.endsWith(".xhtml") || it.endsWith(".htm") }
                .toSortedMap()
                .values
                .forEach { html ->
                    val text = stripHtml(html.toString(Charsets.UTF_8)).trim()
                    if (text.isNotEmpty()) chapters.add(text)
                }
        }
        if (chapters.isEmpty()) throw IllegalArgumentException("EPUB 里没有可读文本")
        val title = Regex("<dc:title[^>]*>([^<]*)</dc:title>").find(opf)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        return SourceDocument(title, chapters, "epub")
    }

    private fun resolve(baseDir: String, href: String): String {
        val clean = href.substringBefore('#')
        if (clean.startsWith("/")) return clean.removePrefix("/")
        return if (baseDir.isEmpty()) clean else "$baseDir/$clean"
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name
                    if (name.endsWith(".opf") || name.endsWith(".xml") || name.endsWith(".html") ||
                        name.endsWith(".xhtml") || name.endsWith(".htm") || name.endsWith(".ncx")
                    ) {
                        result[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
            }
        }
        return result
    }

    /** 把长截图纵向切成若干段（返回每段的 [top, bottom) 像素范围），段间带重叠避免切断文字行。 */
    fun tileRanges(totalHeight: Int, tileHeight: Int, overlap: Int, maxTiles: Int = 8): List<IntRange> {
        if (totalHeight <= 0 || tileHeight <= 0) return emptyList()
        val step = (tileHeight - overlap.coerceIn(0, tileHeight / 2)).coerceAtLeast(tileHeight / 3)
        val ranges = mutableListOf<IntRange>()
        var top = 0
        while (top < totalHeight && ranges.size < maxTiles) {
            val bottom = minOf(top + tileHeight, totalHeight)
            ranges.add(top until bottom)
            if (bottom >= totalHeight) break
            top = bottom - overlap.coerceIn(0, tileHeight / 2)
            if (top <= ranges.last().first) top = ranges.last().first + step
        }
        return ranges
    }

    /** 是否是常见图片格式（按文件头判断）。 */
    fun isImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val webp = bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        return png || jpeg || webp
    }

    /** 把全文按段落切成若干段（每段不超过 maxChars），用于全文分段分析。 */
    fun splitForAnalysis(text: String, maxChars: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val limit = maxChars.coerceAtLeast(2_000)
        if (text.length <= limit) return listOf(text)
        val paragraphs = text.split('\n')
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        paragraphs.forEach { paragraph ->
            val pieces = if (paragraph.length > limit) paragraph.chunked(limit) else listOf(paragraph)
            pieces.forEach { part ->
                if (current.length + part.length + 1 > limit && current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.setLength(0)
                }
                current.append(part).append('\n')
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks.ifEmpty { listOf(text) }
    }

    /** 去 HTML 标签、脚本、常见实体，保留段落换行。 */
    fun stripHtml(html: String): String {
        var text = html
        text = Regex("(?is)<(script|style)[^>]*>.*?</\\1>").replace(text, " ")
        text = Regex("(?i)<br\\s*/?>").replace(text, "\n")
        text = Regex("(?i)</(p|div|h[1-6]|li|tr)>").replace(text, "\n")
        text = Regex("(?s)<[^>]+>").replace(text, " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        text = Regex("&#(\\d+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        return text.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
    }
}
