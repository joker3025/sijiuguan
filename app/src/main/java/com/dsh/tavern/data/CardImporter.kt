package com.dsh.tavern.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object CardImporter {

    data class ImportResult(val card: CharacterCard, val entries: List<WorldEntry>)

    fun importBytes(bytes: ByteArray): ImportResult {
        if (PngCardParser.isPng(bytes)) {
            val meta = PngCardParser.extractMetadata(bytes)
            val raw = meta["ccv3"] ?: meta["chara"]
                ?: throw IllegalArgumentException("PNG 中没有角色卡数据（chara/ccv3）")
            val json = decodeMaybeBase64(raw)
            val root = JSONObject(json)
            val data = root.optJSONObject("data") ?: root
            val card = JsonCodec.cardFromCardJson(root, json)
            val entries = JsonCodec.extractEntries(data, root)
            return ImportResult(card.copy(avatar = makeAvatar(bytes)), entries)
        }

        val text = String(bytes, Charsets.UTF_8).trim().removePrefix("\uFEFF")
        if (!text.startsWith("{")) {
            throw IllegalArgumentException("不是有效的 PNG 或 JSON 角色卡")
        }
        val root = JSONObject(text)
        val data = root.optJSONObject("data") ?: root
        val card = JsonCodec.cardFromCardJson(root, text)
        return ImportResult(card, JsonCodec.extractEntries(data, root))
    }

    private fun decodeMaybeBase64(text: String): String {
        val t = text.trim()
        if (t.startsWith("{")) return t
        return try {
            String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            t
        }
    }

    private fun makeAvatar(bytes: ByteArray): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

            val ratio = minOf(512f / bmp.width, 512f / bmp.height)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(1),
                    (bmp.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bmp
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}
