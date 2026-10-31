package com.dsh.tavern.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** 图片读取工具（本地不做文字识别：图片交给支持视觉的 API 读）。 */
object ImageTextExtractor {

    /** 是否是常见图片格式（按文件头判断）。 */
    fun isImageBytes(bytes: ByteArray): Boolean = SourceImporter.isImageBytes(bytes)

    /** 读取图片并缩放（最长边不超过 maxSide），避免超大截图爆内存。 */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 2000): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** 缩放并压成 JPEG（给视觉 API 用，控制 base64 体积）。 */
    fun loadJpegBytes(context: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 88): ByteArray? {
        val bitmap = loadBitmap(context, uri, maxSide) ?: return null
        val out = java.io.ByteArrayOutputStream()
        return try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 长截图专用：保持宽度（文字清晰），按纵向切成多张带重叠的 JPEG 切片。
     * 普通截图通常只会得到 1 张。
     */
    fun loadTilesAsJpeg(
        context: Context,
        uri: Uri,
        tileWidthMax: Int = 1400,
        tileHeight: Int = 1600,
        quality: Int = 86,
        maxTiles: Int = 8
    ): List<ByteArray> {
        // 先按「宽度不超 tileWidthMax、总高最多 maxTiles 段」限制解码尺寸
        val maxSide = maxOf(tileWidthMax, tileHeight * maxTiles)
        val source = loadBitmap(context, uri, maxSide) ?: return emptyList()
        return try {
            val width = source.width
            val height = source.height
            val scaled = if (width > tileWidthMax) {
                val newHeight = (height.toFloat() * tileWidthMax / width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, tileWidthMax, newHeight, true)
            } else {
                source
            }
            val ranges = SourceImporter.tileRanges(
                scaled.height, tileHeight, overlap = tileHeight / 10, maxTiles = maxTiles
            )
            val tiles = mutableListOf<ByteArray>()
            ranges.forEach { range ->
                val tileHeightPx = range.last - range.first + 1
                if (tileHeightPx <= 0) return@forEach
                val tile = Bitmap.createBitmap(scaled, 0, range.first, scaled.width, tileHeightPx)
                val out = java.io.ByteArrayOutputStream()
                tile.compress(Bitmap.CompressFormat.JPEG, quality, out)
                tile.recycle()
                tiles.add(out.toByteArray())
            }
            tiles
        } finally {
            if (source.isRecycled.not()) source.recycle()
        }
    }
}
