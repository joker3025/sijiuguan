package com.dsh.tavern.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * 把角色卡写成「PNG 内嵌 chara 元数据」的标准格式（SillyTavern / 酒馆通用）。
 * - `chara` 块 = base64(chara_card_v2 JSON)
 * - `ccv3` 块 = base64(chara_card_v3 JSON)
 *
 * 头像不是 PNG（比如导入时压缩成了 JPEG）会先重编码成 PNG；
 * 导出前会清掉原图里已有的 `chara` / `ccv3` 块，避免重复元数据。
 */
object PngCardWriter {

    fun buildCardPng(avatar: ByteArray?, jsonV2: String, jsonV3: String): ByteArray {
        val base = avatarToPng(avatar) ?: defaultPng()
        return embedText(
            base,
            mapOf(
                "chara" to base64(jsonV2),
                "ccv3" to base64(jsonV3)
            )
        )
    }

    private fun base64(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** 头像 → PNG；解码失败返回 null（用占位图）。 */
    private fun avatarToPng(avatar: ByteArray?): ByteArray? {
        if (avatar == null || avatar.isEmpty()) return null
        if (PngCardParser.isPng(avatar)) return avatar
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(avatar, 0, avatar.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.size, opts) ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    private fun defaultPng(): ByteArray {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            Color.rgb(122, 92, 200), Color.rgb(240, 142, 172), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = 150f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("卡", size / 2f, size / 2f + 52f, paint)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** 清掉已有的 chara/ccv3 文本块，在 IHDR 之后插入新的 tEXt 块。 */
    private fun embedText(png: ByteArray, texts: Map<String, String>): ByteArray {
        if (!PngCardParser.isPng(png) || png.size < 33) return png
        val chunks = mutableListOf<ByteArray>()
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0) break
            val total = 12 + length
            if (offset + total > png.size) break
            val type = String(png, offset + 4, 4, Charsets.ISO_8859_1)
            val keyword = if (type == "tEXt" || type == "iTXt" || type == "zTXt") {
                readKeyword(png, offset + 8, length)
            } else {
                ""
            }
            val isOurMeta = keyword.equals("chara", ignoreCase = true) ||
                keyword.equals("ccv3", ignoreCase = true)
            if (!isOurMeta) chunks.add(png.copyOfRange(offset, offset + total))
            offset += total
            if (type == "IEND") break
        }
        if (chunks.isEmpty()) return png

        val out = ByteArrayOutputStream()
        out.write(png, 0, 8)
        var inserted = false
        chunks.forEach { chunk ->
            val type = String(chunk, 4, 4, Charsets.ISO_8859_1)
            out.write(chunk)
            if (!inserted && type == "IHDR") {
                texts.forEach { (keyword, value) ->
                    val data = keyword.toByteArray(Charsets.ISO_8859_1) +
                        byteArrayOf(0) +
                        value.toByteArray(Charsets.ISO_8859_1)
                    writeChunk(out, "tEXt", data)
                }
                inserted = true
            }
        }
        return out.toByteArray()
    }

    /** 读取 tEXt/iTXt/zTXt 的 keyword（到 NUL 为止）。 */
    private fun readKeyword(png: ByteArray, start: Int, length: Int): String {
        val sb = StringBuilder()
        var i = start
        val end = minOf(start + length, png.size)
        while (i < end) {
            val value = png[i].toInt() and 0xFF
            if (value == 0) break
            sb.append(value.toChar())
            i++
        }
        return sb.toString()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeInt(out, data.size)
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(out, crc.value.toInt())
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(byteArrayOf(
            (value ushr 24).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        ))
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
