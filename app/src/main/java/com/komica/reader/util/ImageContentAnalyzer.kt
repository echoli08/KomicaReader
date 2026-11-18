package com.komica.reader.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max

object ImageContentAnalyzer {
    // 空白分隔帶：center/height 以「原始影像像素」為單位。
    data class Gutter(val center: Int, val height: Int)

    // 以縮圖逐列計算「著墨」比例，找出圖片自然的空白分隔帶（例如長條漫畫的頁面間留白）。
    // 需在 IO 執行緒呼叫；解碼失敗或尺寸不合法時回傳 emptyList()，呼叫端可退回幾何平均分配。
    fun Analyze(file: File, imageWidth: Int, imageHeight: Int): List<Gutter> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit(max(1, imageWidth / TargetProfileWidth))
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        try {
            val profileWidth = bitmap.width
            val profileHeight = bitmap.height
            if (profileWidth <= 0 || profileHeight <= 0) return emptyList()

            // 逐列擷取，搭配重用的列緩衝，將記憶體限制在 O(寬度)，避免窄長圖配置過大的像素陣列。
            val rowPixels = IntArray(profileWidth)
            // 忽略過短的空白帶，避免把行間縫隙誤判成頁面分隔。
            val minBandRows = max(2, profileHeight / MinBandDivisor)
            val rawBands = ArrayList<Gutter>()
            var bandStart = -1
            var bandRows = 0
            for (y in 0 until profileHeight) {
                bitmap.getPixels(rowPixels, 0, profileWidth, 0, y, profileWidth, 1)
                var ink = 0
                for (x in 0 until profileWidth) {
                    val p = rowPixels[x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    if (0.299f * r + 0.587f * g + 0.114f * b < InkLuminanceThreshold) ink++
                }
                if (ink.toFloat() / profileWidth.toFloat() <= BlankInkFraction) {
                    if (bandStart < 0) {
                        bandStart = y
                        bandRows = 1
                    } else {
                        bandRows++
                    }
                } else if (bandStart >= 0) {
                    if (bandRows >= minBandRows) rawBands.add(Gutter(bandStart + bandRows / 2, bandRows))
                    bandStart = -1
                    bandRows = 0
                }
            }
            // 圖片結尾仍為空白帶時補上最後一段。
            if (bandStart >= 0 && bandRows >= minBandRows) {
                rawBands.add(Gutter(bandStart + bandRows / 2, bandRows))
            }

            // 將縮圖座標轉回原始影像像素。
            val scaleY = imageHeight.toFloat() / profileHeight.toFloat()
            return rawBands
                .map { Gutter((it.center * scaleY).toInt(), max(1, (it.height * scaleY).toInt())) }
                .sortedBy { it.center }
        } finally {
            bitmap.recycle()
        }
    }

    private const val TargetProfileWidth = 240
    private const val MinBandDivisor = 500
    private const val InkLuminanceThreshold = 230f
    private const val BlankInkFraction = 0.02f
}
