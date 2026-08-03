package com.rokid.cxrmsamples.activities.model

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils

class ImageSegmentationEngine(
    private val moduleStage1: Module,
    private val moduleStage2: Module,
    private val classNumStage1: Int,
    private val classNumStage2: Int,
    private val stage1HealthyIdx: Int,
    private val stage1BgIdx: Int,
    private val stage2HealthyIdx: Int,
    private val stage2StripIdx: Int,
    private val stage2BgIdx: Int
) {

    data class SegmentationResult(
        val bitmap: Bitmap,
        val percent: Float,
        val runtimeMs: Long,
        val stage1RuntimeMs: Long,
        val stage2RuntimeMs: Long,
        val redCount: Float,
        val greenCount: Float,
        val diseaseLevel: String = "level1",
        val stage3RuntimeMs: Long = 0L
    )

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

    fun run(inputBitmap: Bitmap): SegmentationResult? = runSegmentation(inputBitmap)

    fun run(inputBitmap: Bitmap, onProgress: (String) -> Unit): SegmentationResult? =
        runSegmentation(inputBitmap, onProgress)

    fun runSegmentation(inputBitmap: Bitmap): SegmentationResult? =
        runSegmentation(inputBitmap) {}

    fun runSegmentation(inputBitmap: Bitmap, onProgress: (String) -> Unit): SegmentationResult? {
        val pipelineStart = System.currentTimeMillis()
        val width2 = inputBitmap.width
        val height2 = inputBitmap.height

        onProgress("Stage 1 coarse segmentation (${width2}x${height2})...")
        // Stage1 输入
        val inputTensorStage1 = TensorImageUtils.bitmapToFloat32Tensor(
            inputBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        val stage1Start = SystemClock.elapsedRealtime()
        // Java 代码里 inferenceTime_2 未包含 forward，这里直接测 forward
        val outputTensorStage1 = moduleStage1.forward(IValue.from(inputTensorStage1)).toTensor()
        val stage1Runtime = SystemClock.elapsedRealtime() - stage1Start

        val scores1 = outputTensorStage1.dataAsFloatArray
        val intValues1 = IntArray(width2 * height2)
        for (j in 0 until height2) {
            for (k in 0 until width2) {
                var maxIdx = 0
                var maxScore = Double.NEGATIVE_INFINITY
                for (c in 0 until classNumStage1) {
                    val score = scores1[c * (width2 * height2) + j * width2 + k].toDouble()
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }
                if (maxIdx == stage1HealthyIdx) {
                    intValues1[j * width2 + k] = 0xFF00FF00.toInt()
                } else if (maxIdx == stage1BgIdx) {
                    intValues1[j * width2 + k] = Color.BLACK
                }
            }
        }

        // 对齐原 Java 的多次缩放与掩码拷贝流程
        val bmpSegmentation2 = Bitmap.createScaledBitmap(inputBitmap, width2, height2, true)
        val outputBitmap2 = bmpSegmentation2.copy(bmpSegmentation2.config ?: Bitmap.Config.ARGB_8888, true)
        val bmpSegmentation31 = Bitmap.createScaledBitmap(inputBitmap, width2, height2, true)
        val bmpSegmentation3 = bmpSegmentation31.copy(bmpSegmentation31.config ?: Bitmap.Config.ARGB_8888, true)
        outputBitmap2.setPixels(intValues1, 0, outputBitmap2.width, 0, 0, outputBitmap2.width, outputBitmap2.height)
        val transferredBitmap2 = Bitmap.createScaledBitmap(outputBitmap2, inputBitmap.width, inputBitmap.height, true)
        for (i in 0 until inputBitmap.width) {
            for (j in 0 until inputBitmap.height) {
                if (transferredBitmap2.getPixel(i, j) == Color.BLACK) {
                    bmpSegmentation3.setPixel(i, j, Color.BLACK)
                }
            }
        }
        val transferredBitmap3 = Bitmap.createScaledBitmap(bmpSegmentation3, inputBitmap.width, inputBitmap.height, true)

        onProgress("Stage 1 complete (${stage1Runtime}ms). Preparing Stage 2...")
        // Stage2 输入
        val inputTensorStage2 = TensorImageUtils.bitmapToFloat32Tensor(
            transferredBitmap3,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        val stage2Start = SystemClock.elapsedRealtime()
        onProgress("Stage 2 fine segmentation...")
        val outputTensorStage2 = moduleStage2.forward(IValue.from(inputTensorStage2)).toTensor()
        val stage2Runtime = SystemClock.elapsedRealtime() - stage2Start

        val scores2 = outputTensorStage2.dataAsFloatArray
        val width = transferredBitmap2.width
        val height = transferredBitmap2.height
        val intValues2 = IntArray(width * height)
        var redCount = 0f
        var greenCount = 0f
        for (j in 0 until height) {
            for (k in 0 until width) {
                var maxIdx = 0
                var maxScore = Double.NEGATIVE_INFINITY
                for (c in 0 until classNumStage2) {
                    val score = scores2[c * (width * height) + j * width + k].toDouble()
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }
                val idx = j * width + k
                when (maxIdx) {
                    stage2HealthyIdx -> {
                        intValues2[idx] = 0xFF008000.toInt()
                        greenCount++
                    }
                    stage2BgIdx -> intValues2[idx] = Color.BLACK
                    stage2StripIdx -> {
                        intValues2[idx] = 0xFF800000.toInt()
                        redCount++
                    }
                    else -> intValues2[idx] = Color.BLACK
                }
            }
        }

        val bmpSegmentation = Bitmap.createScaledBitmap(transferredBitmap3, width, height, true)
        val outputBitmap = bmpSegmentation.copy(bmpSegmentation.config ?: Bitmap.Config.ARGB_8888, true)
        outputBitmap.setPixels(intValues2, 0, outputBitmap.width, 0, 0, outputBitmap.width, outputBitmap.height)
        val transferredBitmap = Bitmap.createScaledBitmap(outputBitmap, transferredBitmap3.width, transferredBitmap3.height, true)

        val percent = if (redCount + greenCount == 0f) 0f else redCount / (redCount + greenCount)
        val totalRuntime = System.currentTimeMillis() - pipelineStart
        Log.i(TAG, "Segmentation finished total=${totalRuntime}ms (stage1=${stage1Runtime}ms, stage2=${stage2Runtime}ms)")

        return SegmentationResult(
            bitmap = transferredBitmap,
            percent = percent,
            runtimeMs = totalRuntime,
            stage1RuntimeMs = stage1Runtime,
            stage2RuntimeMs = stage2Runtime,
            redCount = redCount,
            greenCount = greenCount
        )
    }

    // 预热：用小图跑一次两阶段，消除首次推理与内存分配开销
    fun warmup() {
        try {
            val side = 256
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvasStart = System.currentTimeMillis()
            // 填充为深灰，减少全黑对模型的特殊性
            val fill = IntArray(side * side) { 0xFF404040.toInt() }
            bmp.setPixels(fill, 0, side, 0, 0, side, side)
            Log.d(TAG, "Warmup: build bitmap ${System.currentTimeMillis() - canvasStart}ms")

            val t1 = System.currentTimeMillis()
            val out1 = forward(moduleStage1, bitmapToTensor(bmp))
            Log.d(TAG, "Warmup: stage1 ${System.currentTimeMillis() - t1}ms (out=${out1.size})")

            val t2 = System.currentTimeMillis()
            val out2 = forward(moduleStage2, bitmapToTensor(bmp))
            Log.d(TAG, "Warmup: stage2 ${System.currentTimeMillis() - t2}ms (out=${out2.size})")
        } catch (e: Throwable) {
            Log.w(TAG, "Warmup failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ImageSegEngine"
    }
}
