package com.dsh.tavern.data

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * 解析 PNG 角色卡：SillyTavern 把 chara_card_v2/v3 JSON 放在 tEXt/iTXt/zTXt 块里。
 * 支持关键字 chara 与 ccv3。
 */
object PngCardParser {
    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        for (i in SIGNATURE.indices) {
            if (bytes[i] != SIGNATURE[i]) return false
        }
        return true
    }

    fun extractMetadata(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!isPng(bytes)) return result
        var pos = 8
        while (pos + 12 <= bytes.size) {
            val length = readInt(bytes, pos)
            if (length < 0 || pos + 12L + length > bytes.size) break
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val data = bytes.copyOfRange(pos + 8, pos + 8 + length)
            when (type) {
                "tEXt" -> parseText(data)?.let { (k, v) -> result[k] = v }
                "zTXt" -> parseCompressedText(data)?.let { (k, v) -> result[k] = v }
                "iTXt" -> parseInternationalText(data)?.let { (k, v) -> result[k] = v }
                "IEND" -> break
            }
            pos += 12 + length
        }
        return result
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun findZero(data: ByteArray, from: Int): Int {
        var i = from
        while (i < data.size) {
            if (data[i].toInt() == 0) return i
            i++
        }
        return -1
    }

    private fun parseText(data: ByteArray): Pair<String, String>? {
        val zero = findZero(data, 0)
        if (zero <= 0) return null
        val keyword = String(data, 0, zero, Charsets.ISO_8859_1)
        val text = decodeUtf8OrLatin1(data, zero + 1, data.size - zero - 1)
        return keyword to text
    }

    /** 多数工具按 UTF-8 写入 tEXt（严格来说是 Latin-1）；两种都兼容。 */
    private fun decodeUtf8OrLatin1(data: ByteArray, offset: Int, length: Int): String {
        val utf8 = String(data, offset, length, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            String(data, offset, length, Charsets.ISO_8859_1)
        } else {
            utf8
        }
    }

    private fun parseCompressedText(data: ByteArray): Pair<String, String>? {
        val zero = findZero(data, 0)
        if (zero <= 0 || zero + 2 > data.size) return null
        val keyword = String(data, 0, zero, Charsets.ISO_8859_1)
        val compressed = data.copyOfRange(zero + 2, data.size)
        val text = try {
            String(inflate(compressed), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        return keyword to text
    }

    private fun parseInternationalText(data: ByteArray): Pair<String, String>? {
        val kZero = findZero(data, 0)
        if (kZero <= 0 || kZero + 3 > data.size) return null
        val keyword = String(data, 0, kZero, Charsets.ISO_8859_1)
        val compressed = (data[kZero + 1].toInt() and 0xFF) == 1
        var p = kZero + 3
        val langZero = findZero(data, p)
        if (langZero < 0) return null
        p = langZero + 1
        val transZero = findZero(data, p)
        if (transZero < 0) return null
        p = transZero + 1
        if (p > data.size) return null
        val raw = data.copyOfRange(p, data.size)
        val text = try {
            val decoded = if (compressed) inflate(raw) else raw
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        return keyword to text
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
