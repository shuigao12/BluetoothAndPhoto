package com.rokid.cxrmsamples.activities.model

import android.graphics.Bitmap
import android.graphics.Color
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils

class ImageSegmentationEngine(
    private val moduleStage1: Module,
    private val moduleStage2: Module,
    private val classNumStage1: Int,
    private val classNumStage2: Int,
    private val idxHealthy: Int,
    private val idxBg: Int,
    private val idxStrip: Int
) {

    data class SegmentationResult(
        val bitmap: Bitmap,
        val percent: Float,
        val runtimeMs: Long
    )

    fun runSegmentation(inputBitmap: Bitmap): SegmentationResult {
        val startPipeline = System.currentTimeMillis()

        // ---------- Stage 1 ----------
        val tensor1 = bitmapToTensor(inputBitmap)
        val output1 = forward(moduleStage1, tensor1)
        val maskBitmap = generateMaskBitmap(output1, inputBitmap.width, inputBitmap.height)

        // ---------- Mask merge ----------
        val merged = applyMaskOnBitmap(inputBitmap, maskBitmap)

        // ---------- Stage 2 ----------
        val tensor2 = bitmapToTensor(merged)
        val output2 = forward(moduleStage2, tensor2)

        val (coloredBitmap, percent) = applyColorMapAndStatistics(
            output2,
            merged.width,
            merged.height
        )

        val endPipeline = System.currentTimeMillis()

        return SegmentationResult(
            bitmap = coloredBitmap,
            percent = percent,
            runtimeMs = endPipeline - startPipeline
        )
    }

    // ---------------------------------------------------------
    //                       Helpers
    // ---------------------------------------------------------

    private fun bitmapToTensor(bmp: Bitmap): Tensor {
        return TensorImageUtils.bitmapToFloat32Tensor(
            bmp,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
    }

    private fun forward(module: Module, tensor: Tensor): FloatArray {
        return module.forward(IValue.from(tensor)).toTensor().dataAsFloatArray
    }

    /** 第一阶段：生成绿色/黑色的 mask */
    private fun generateMaskBitmap(scores: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxIdx = 0
                var maxScore = Float.NEGATIVE_INFINITY

                for (c in 0 until classNumStage1) {
                    val score = scores[c * width * height + y * width + x]
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }

                pixels[y * width + x] = when (maxIdx) {
                    idxHealthy -> Color.GREEN // 0xFF00FF00
                    idxBg -> Color.BLACK      // 0xFF000000
                    else -> Color.BLACK
                }
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 把黑色区域抹到原图上 */
    private fun applyMaskOnBitmap(original: Bitmap, mask: Bitmap): Bitmap {
        val w = original.width
        val h = original.height

        val output = original.copy(Bitmap.Config.ARGB_8888, true)

        for (x in 0 until w) {
            for (y in 0 until h) {
                if (mask.getPixel(x, y) == Color.BLACK) {
                    output.setPixel(x, y, Color.BLACK)
                }
            }
        }
        return output
    }

    /** 第二阶段：着色、统计 red/green 占比 */
    private fun applyColorMapAndStatistics(
        scores: FloatArray,
        width: Int,
        height: Int
    ): Pair<Bitmap, Float> {

        val pixels = IntArray(width * height)
        var redCount = 0f
        var greenCount = 0f

        for (y in 0 until height) {
            for (x in 0 until width) {

                var maxIdx = 0
                var maxScore = Float.NEGATIVE_INFINITY

                for (c in 0 until classNumStage2) {
                    val score = scores[c * width * height + y * width + x]
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }

                when (maxIdx) {
                    idxHealthy -> {
                        pixels[y * width + x] = Color.rgb(0, 128, 0)   // 深绿 (0xFF008000)
                        greenCount++
                    }
                    idxStrip -> {
                        pixels[y * width + x] = Color.rgb(128, 0, 0)   // 深红 (0xFF800000)
                        redCount++
                    }
                    else -> {
                        pixels[y * width + x] = Color.BLACK
                    }
                }
            }
        }

        val percent = if (redCount + greenCount == 0f) 0f else redCount / (redCount + greenCount)

        val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return Pair(bmp, percent)
    }
}
