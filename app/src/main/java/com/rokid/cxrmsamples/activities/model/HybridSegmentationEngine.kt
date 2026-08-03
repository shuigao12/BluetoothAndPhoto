package com.rokid.cxrmsamples.activities.model

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.torchvision.TensorImageUtils
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

interface SegmentationEngine {
    fun warmup()
    fun run(inputBitmap: Bitmap): ImageSegmentationEngine.SegmentationResult?
    fun run(inputBitmap: Bitmap, onProgress: (String) -> Unit): ImageSegmentationEngine.SegmentationResult? =
        run(inputBitmap)
}

interface StageBackend : Closeable {
    fun run(inputBitmap: Bitmap): FloatArray
}

interface ClassificationBackend : Closeable {
    fun run(inputBitmap: Bitmap): FloatArray
}

data class StageLayout(
    val classCount: Int,
    val healthyIdx: Int,
    val bgIdx: Int,
    val redIdx: Int? = null
)

class LegacyTwoStageSegmentationEngine(
    private val delegate: ImageSegmentationEngine
) : SegmentationEngine {
    override fun warmup() {
        delegate.warmup()
    }

    override fun run(inputBitmap: Bitmap): ImageSegmentationEngine.SegmentationResult? {
        return delegate.run(inputBitmap)
    }

    override fun run(inputBitmap: Bitmap, onProgress: (String) -> Unit): ImageSegmentationEngine.SegmentationResult? {
        onProgress("Stage 1 coarse segmentation...")
        return delegate.run(inputBitmap, onProgress)
    }
}

class GenericTwoStageSegmentationEngine(
    private val stage1Backend: StageBackend,
    private val stage2Backend: StageBackend,
    private val stage1Layout: StageLayout,
    private val stage2Layout: StageLayout,
    private val engineName: String
) : SegmentationEngine {

    override fun warmup() {
        try {
            val side = 256
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val fill = IntArray(side * side) { 0xFF404040.toInt() }
            bmp.setPixels(fill, 0, side, 0, 0, side, side)
            stage1Backend.run(bmp)
            stage2Backend.run(bmp)
        } catch (e: Throwable) {
            Log.w(TAG, "$engineName warmup failed: ${e.message}", e)
        }
    }

    override fun run(inputBitmap: Bitmap): ImageSegmentationEngine.SegmentationResult? {
        return run(inputBitmap) {}
    }

    override fun run(inputBitmap: Bitmap, onProgress: (String) -> Unit): ImageSegmentationEngine.SegmentationResult? {
        val pipelineStart = System.currentTimeMillis()
        return try {
            val width = inputBitmap.width
            val height = inputBitmap.height
            val pixelCount = width * height
            if (pixelCount <= 0) {
                Log.w(TAG, "$engineName invalid bitmap size: ${width}x${height}")
                return null
            }

            onProgress("Stage 1 coarse segmentation (${width}x${height})...")
            val stage1Start = System.currentTimeMillis()
            val stage1Scores = stage1Backend.run(inputBitmap)
            val stage1Mask = buildMaskBitmap(
                scores = stage1Scores,
                width = width,
                height = height,
                layout = stage1Layout
            )
            onProgress("Stage 1 complete (${System.currentTimeMillis() - stage1Start}ms). Preparing Stage 2...")

            val stage2Input = applyMaskToBitmap(inputBitmap, stage1Mask.bitmap)
            val stage2Start = System.currentTimeMillis()
            onProgress("Stage 2 fine segmentation...")
            val stage2Scores = stage2Backend.run(stage2Input)
            val stage2Runtime = System.currentTimeMillis() - stage2Start
            val stage2Mask = buildMaskBitmap(
                scores = stage2Scores,
                width = width,
                height = height,
                layout = stage2Layout
            )

            val totalForeground = stage2Mask.redCount + stage2Mask.greenCount
            val percent = if (totalForeground == 0f) 0f else stage2Mask.redCount / totalForeground
            val totalRuntime = System.currentTimeMillis() - pipelineStart

            Log.i(
                TAG,
                "$engineName finished total=${totalRuntime}ms, stage1=${stage1Mask.runtimeMs}ms, stage2=${stage2Runtime}ms"
            )

            ImageSegmentationEngine.SegmentationResult(
                bitmap = stage2Mask.bitmap,
                percent = percent,
                runtimeMs = totalRuntime,
                stage1RuntimeMs = stage1Mask.runtimeMs,
                stage2RuntimeMs = stage2Runtime,
                redCount = stage2Mask.redCount,
                greenCount = stage2Mask.greenCount,
                diseaseLevel = "level1"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "$engineName segmentation failed: ${e.message}", e)
            null
        }
    }

    private data class MaskResult(
        val bitmap: Bitmap,
        val redCount: Float,
        val greenCount: Float,
        val runtimeMs: Long
    )

    private fun buildMaskBitmap(
        scores: FloatArray,
        width: Int,
        height: Int,
        layout: StageLayout
    ): MaskResult {
        val pixelCount = width * height
        if (scores.isEmpty() || scores.size % pixelCount != 0) {
            throw IllegalArgumentException("Invalid model output size=${scores.size} for pixelCount=$pixelCount")
        }

        val classCount = max(1, scores.size / pixelCount)
        val intValues = IntArray(pixelCount)
        var redCount = 0f
        var greenCount = 0f
        val start = System.currentTimeMillis()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                var maxIdx = 0
                var maxScore = Double.NEGATIVE_INFINITY

                for (c in 0 until classCount) {
                    val score = scores[c * pixelCount + index].toDouble()
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }

                when {
                    maxIdx == layout.bgIdx -> intValues[index] = Color.BLACK
                    maxIdx == layout.healthyIdx -> {
                        intValues[index] = 0xFF008000.toInt()
                        greenCount++
                    }
                    layout.redIdx != null && maxIdx == layout.redIdx -> {
                        intValues[index] = 0xFF800000.toInt()
                        redCount++
                    }
                    layout.redIdx == null && maxIdx != layout.bgIdx -> {
                        intValues[index] = 0xFF800000.toInt()
                        redCount++
                    }
                    else -> intValues[index] = Color.BLACK
                }
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(intValues, 0, width, 0, 0, width, height)
        return MaskResult(bitmap, redCount, greenCount, System.currentTimeMillis() - start)
    }

    private fun applyMaskToBitmap(source: Bitmap, mask: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask.getPixel(x, y) == Color.BLACK) {
                    result.setPixel(x, y, Color.BLACK)
                }
            }
        }
        return result
    }

    companion object {
        private const val TAG = "HybridSegEngine"
    }
}

class ThreeStageSegmentationEngine(
    private val stage1Backend: StageBackend,
    private val stage2Backend: StageBackend,
    private val classificationBackend: ClassificationBackend,
    private val stage1Layout: StageLayout,
    private val stage2Layout: StageLayout,
    private val engineName: String
) : SegmentationEngine {

    override fun warmup() {
        try {
            val side = STAGE2_INPUT_SIZE
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val fill = IntArray(side * side) { 0xFF404040.toInt() }
            bmp.setPixels(fill, 0, side, 0, 0, side, side)
            stage1Backend.run(bmp)
            stage2Backend.run(bmp)
            classificationBackend.run(Bitmap.createScaledBitmap(bmp, STAGE3_INPUT_SIZE, STAGE3_INPUT_SIZE, true))
        } catch (e: Throwable) {
            Log.w(TAG, "$engineName warmup failed: ${e.message}", e)
        }
    }

    override fun run(inputBitmap: Bitmap): ImageSegmentationEngine.SegmentationResult? = run(inputBitmap) {}

    override fun run(
        inputBitmap: Bitmap,
        onProgress: (String) -> Unit
    ): ImageSegmentationEngine.SegmentationResult? {
        val pipelineStart = System.currentTimeMillis()
        return try {
            val width = inputBitmap.width
            val height = inputBitmap.height
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "$engineName invalid bitmap size: ${width}x${height}")
                return null
            }

            onProgress("Stage 1 coarse segmentation (${width}x${height})...")
            val stage1Start = System.currentTimeMillis()
            val stage1Scores = stage1Backend.run(inputBitmap)
            val stage1MaskSmall = buildMaskBitmap(
                scores = stage1Scores,
                width = STAGE2_INPUT_SIZE,
                height = STAGE2_INPUT_SIZE,
                layout = stage1Layout
            )
            val stage1MaskBitmap = Bitmap.createScaledBitmap(
                stage1MaskSmall.bitmap,
                width,
                height,
                false
            )
            val stage1Mask = recountMask(stage1MaskBitmap, stage1Layout)
            val stage1Runtime = System.currentTimeMillis() - stage1Start
            if (stage1Mask.greenCount <= 0f) {
                Log.w(TAG, "$engineName no leaf detected in stage1")
                stage1MaskBitmap.recycle()
                return null
            }

            onProgress("Stage 1 complete (${stage1Runtime}ms). Preparing Stage 2...")
            val leafMasked = applyMaskToBitmap(inputBitmap, stage1MaskBitmap)
            stage1MaskSmall.bitmap.recycle()
            stage1MaskBitmap.recycle()

            onProgress("Stage 2 fine segmentation (${STAGE2_INPUT_SIZE}x${STAGE2_INPUT_SIZE})...")
            val stage2Start = System.currentTimeMillis()
            val stage2Scores = stage2Backend.run(leafMasked)
            val stage2Runtime = System.currentTimeMillis() - stage2Start
            val stage2MaskSmall = buildMaskBitmap(
                scores = stage2Scores,
                width = STAGE2_INPUT_SIZE,
                height = STAGE2_INPUT_SIZE,
                layout = stage2Layout
            )
            val stage2MaskBitmap = Bitmap.createScaledBitmap(
                stage2MaskSmall.bitmap,
                width,
                height,
                true
            )
            val stage2Mask = recountMask(stage2MaskBitmap, stage2Layout)

            val totalForeground = stage2Mask.redCount + stage2Mask.greenCount
            val percent = if (totalForeground == 0f) 0f else stage2Mask.redCount / totalForeground

            onProgress("Stage 3 disease classification (${STAGE3_INPUT_SIZE}x${STAGE3_INPUT_SIZE})...")
            val stage3Start = System.currentTimeMillis()
            val clsInput = Bitmap.createScaledBitmap(leafMasked, STAGE3_INPUT_SIZE, STAGE3_INPUT_SIZE, true)
            val logits = try {
                classificationBackend.run(clsInput)
            } finally {
                if (clsInput !== leafMasked) clsInput.recycle()
            }
            val diseaseLevel = DiseaseLevel.argmax(logits)
            val stage3Runtime = System.currentTimeMillis() - stage3Start

            val totalRuntime = System.currentTimeMillis() - pipelineStart
            Log.i(
                TAG,
                "$engineName finished total=${totalRuntime}ms, stage1=${stage1Runtime}ms, stage2=${stage2Runtime}ms, stage3=${stage3Runtime}ms, level=$diseaseLevel"
            )

            ImageSegmentationEngine.SegmentationResult(
                bitmap = stage2Mask.bitmap,
                percent = percent,
                runtimeMs = totalRuntime,
                stage1RuntimeMs = stage1Runtime,
                stage2RuntimeMs = stage2Runtime,
                redCount = stage2Mask.redCount,
                greenCount = stage2Mask.greenCount,
                diseaseLevel = diseaseLevel,
                stage3RuntimeMs = stage3Runtime
            )
        } catch (e: Throwable) {
            Log.e(TAG, "$engineName segmentation failed: ${e.message}", e)
            null
        }
    }

    private data class MaskResult(
        val bitmap: Bitmap,
        val redCount: Float,
        val greenCount: Float
    )

    private fun recountMask(bitmap: Bitmap, layout: StageLayout): MaskResult {
        val width = bitmap.width
        val height = bitmap.height
        var redCount = 0f
        var greenCount = 0f
        for (y in 0 until height) {
            for (x in 0 until width) {
                when (bitmap.getPixel(x, y)) {
                    0xFF008000.toInt() -> greenCount++
                    0xFF800000.toInt() -> redCount++
                }
            }
        }
        return MaskResult(bitmap, redCount, greenCount)
    }

    private fun buildMaskBitmap(
        scores: FloatArray,
        width: Int,
        height: Int,
        layout: StageLayout
    ): MaskResult {
        val pixelCount = width * height
        if (scores.isEmpty() || scores.size % pixelCount != 0) {
            throw IllegalArgumentException("Invalid model output size=${scores.size} for pixelCount=$pixelCount")
        }

        val classCount = max(1, scores.size / pixelCount)
        val intValues = IntArray(pixelCount)
        var redCount = 0f
        var greenCount = 0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                var maxIdx = 0
                var maxScore = Double.NEGATIVE_INFINITY

                for (c in 0 until classCount) {
                    val score = scores[c * pixelCount + index].toDouble()
                    if (score > maxScore) {
                        maxScore = score
                        maxIdx = c
                    }
                }

                when {
                    maxIdx == layout.bgIdx -> intValues[index] = Color.BLACK
                    maxIdx == layout.healthyIdx -> {
                        intValues[index] = 0xFF008000.toInt()
                        greenCount++
                    }
                    layout.redIdx != null && maxIdx == layout.redIdx -> {
                        intValues[index] = 0xFF800000.toInt()
                        redCount++
                    }
                    layout.redIdx == null && maxIdx != layout.bgIdx -> {
                        intValues[index] = 0xFF800000.toInt()
                        redCount++
                    }
                    else -> intValues[index] = Color.BLACK
                }
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(intValues, 0, width, 0, 0, width, height)
        return MaskResult(bitmap, redCount, greenCount)
    }

    private fun applyMaskToBitmap(source: Bitmap, mask: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask.getPixel(x, y) == Color.BLACK) {
                    result.setPixel(x, y, Color.BLACK)
                }
            }
        }
        return result
    }

    companion object {
        private const val TAG = "ThreeStageEngine"
        private const val STAGE2_INPUT_SIZE = 512
        private const val STAGE3_INPUT_SIZE = 224
    }
}

class PtlStageBackend(
    modelFile: File,
    private val inputSize: Int = 512
) : StageBackend {
    private val module = LiteModuleLoader.load(modelFile.absolutePath)

    override fun run(inputBitmap: Bitmap): FloatArray {
        val working = if (inputBitmap.width == inputSize && inputBitmap.height == inputSize) {
            inputBitmap
        } else {
            Bitmap.createScaledBitmap(inputBitmap, inputSize, inputSize, true)
        }
        return try {
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                working,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )
            module.forward(IValue.from(inputTensor)).toTensor().dataAsFloatArray
        } finally {
            if (working !== inputBitmap) {
                working.recycle()
            }
        }
    }

    override fun close() {
        // LiteModuleLoader doesn't expose an explicit close.
    }
}

class PtlClassificationBackend(
    modelFile: File,
    private val inputSize: Int = 224
) : ClassificationBackend {
    private val module = LiteModuleLoader.load(modelFile.absolutePath)

    override fun run(inputBitmap: Bitmap): FloatArray {
        val working = if (inputBitmap.width == inputSize && inputBitmap.height == inputSize) {
            inputBitmap
        } else {
            Bitmap.createScaledBitmap(inputBitmap, inputSize, inputSize, true)
        }
        return try {
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                working,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )
            module.forward(IValue.from(inputTensor)).toTensor().dataAsFloatArray
        } finally {
            if (working !== inputBitmap) {
                working.recycle()
            }
        }
    }

    override fun close() {
        // LiteModuleLoader doesn't expose an explicit close.
    }
}

private const val ONNX_TAG = "OnnxBackend"

/** XNNPACK INT8 在 Android ORT 上需 BASIC_OPT，且失败时回退 CPU */
private class OrtRunner(
    private val environment: OrtEnvironment,
    modelFile: File,
    wantXnnpack: Boolean,
    preferBasicGraphOpt: Boolean
) : Closeable {
    private val modelPath: String = modelFile.absolutePath
    private val preferXnnpack: Boolean = wantXnnpack
    private val preferredOptLevel: OrtSession.SessionOptions.OptLevel =
        if (preferBasicGraphOpt) {
            OrtSession.SessionOptions.OptLevel.BASIC_OPT
        } else {
            OrtSession.SessionOptions.OptLevel.ALL_OPT
        }
    private var session: OrtSession? = null
    private var activeXnnpack: Boolean = false
    private var activeOptLevel: OrtSession.SessionOptions.OptLevel = preferredOptLevel
    private var inputName: String = ""

    fun run(inputBuffer: FloatBuffer, shape: LongArray): FloatArray {
        ensureSession(preferXnnpack = preferXnnpack, optLevel = preferredOptLevel)
        return runWithFallback(inputBuffer, shape)
    }

    private fun runWithFallback(inputBuffer: FloatBuffer, shape: LongArray): FloatArray {
        return try {
            runInternal(inputBuffer, shape)
        } catch (e: OrtException) {
            if (activeXnnpack && shouldFallbackToCpu(e)) {
                Log.w(ONNX_TAG, "XNNPACK infer failed (${e.message}), fallback CPU: $modelPath")
                recreateSession(useXnnpack = false, optLevel = activeOptLevel)
                inputBuffer.rewind()
                runWithFallback(inputBuffer, shape)
            } else if (shouldFallbackGraphOpt(e)) {
                val next = nextLowerOptLevel(activeOptLevel)
                Log.w(ONNX_TAG, "Graph opt ${activeOptLevel.name} failed (${e.message}), retry ${next.name}: $modelPath")
                recreateSession(useXnnpack = false, optLevel = next)
                inputBuffer.rewind()
                runWithFallback(inputBuffer, shape)
            } else {
                throw e
            }
        }
    }

    private fun ensureSession(
        preferXnnpack: Boolean,
        optLevel: OrtSession.SessionOptions.OptLevel
    ) {
        if (session != null) return
        if (preferXnnpack) {
            try {
                openSession(useXnnpack = true, optLevel = optLevel)
                return
            } catch (e: OrtException) {
                if (shouldFallbackToCpu(e)) {
                    Log.w(ONNX_TAG, "XNNPACK session failed (${e.message}), fallback CPU: $modelPath")
                } else {
                    throw e
                }
            }
        }
        openSession(useXnnpack = false, optLevel = optLevel)
    }

    private fun recreateSession(
        useXnnpack: Boolean,
        optLevel: OrtSession.SessionOptions.OptLevel
    ) {
        session?.close()
        session = null
        openSession(useXnnpack = useXnnpack, optLevel = optLevel)
    }

    private fun openSession(
        useXnnpack: Boolean,
        optLevel: OrtSession.SessionOptions.OptLevel
    ) {
        val created = environment.createSession(
            modelPath,
            createOnnxSessionOptions(useXnnpack, optLevel)
        )
        session = created
        activeXnnpack = useXnnpack
        activeOptLevel = optLevel
        inputName = created.inputNames.first()
        Log.i(
            ONNX_TAG,
            "Session ready (${if (useXnnpack) "XNNPACK" else "CPU"}, ${optLevel.name}): ${modelFile.name}"
        )
    }

    private val modelFile: File get() = File(modelPath)

    private fun runInternal(inputBuffer: FloatBuffer, shape: LongArray): FloatArray {
        val current = session ?: error("ONNX session not initialized")
        OnnxTensor.createTensor(environment, inputBuffer, shape).use { inputTensor ->
            current.run(mapOf(inputName to inputTensor)).use { result ->
                return flattenToFloatArray(result[0].value)
            }
        }
    }

    override fun close() {
        session?.close()
        session = null
    }

    private fun shouldFallbackToCpu(e: OrtException): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("NOT_IMPLEMENTED", ignoreCase = true) ||
            msg.contains("XnnpackExecutionProvider", ignoreCase = true) ||
            msg.contains("FusedNodeAndGraph", ignoreCase = true)
    }

    private fun shouldFallbackGraphOpt(e: OrtException): Boolean {
        if (activeOptLevel == OrtSession.SessionOptions.OptLevel.NO_OPT) return false
        val msg = e.message.orEmpty()
        return msg.contains("Reshape", ignoreCase = true) ||
            msg.contains("ORT_RUNTIME_EXCEPTION", ignoreCase = true) ||
            msg.contains("QLinear", ignoreCase = true)
    }

    private fun nextLowerOptLevel(current: OrtSession.SessionOptions.OptLevel): OrtSession.SessionOptions.OptLevel {
        return when (current) {
            OrtSession.SessionOptions.OptLevel.ALL_OPT -> OrtSession.SessionOptions.OptLevel.BASIC_OPT
            OrtSession.SessionOptions.OptLevel.EXTENDED_OPT -> OrtSession.SessionOptions.OptLevel.BASIC_OPT
            OrtSession.SessionOptions.OptLevel.BASIC_OPT -> OrtSession.SessionOptions.OptLevel.NO_OPT
            else -> OrtSession.SessionOptions.OptLevel.NO_OPT
        }
    }
}

class OnnxStageBackend(
    modelFile: File,
    useXnnpack: Boolean = false,
    private val fixedInputSize: Int? = null,
    useBasicGraphOpt: Boolean = false
) : StageBackend {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val runner = OrtRunner(environment, modelFile, useXnnpack, useBasicGraphOpt)

    override fun run(inputBitmap: Bitmap): FloatArray {
        val working = if (fixedInputSize != null) {
            Bitmap.createScaledBitmap(inputBitmap, fixedInputSize, fixedInputSize, true)
        } else {
            inputBitmap
        }
        return try {
            val tensorBuffer = bitmapToNormalizedFloatBuffer(working)
            val shape = longArrayOf(1, 3, working.height.toLong(), working.width.toLong())
            runner.run(tensorBuffer, shape)
        } finally {
            if (working !== inputBitmap) {
                working.recycle()
            }
        }
    }

    override fun close() {
        runner.close()
    }
}

class OnnxClassificationBackend(
    modelFile: File,
    useXnnpack: Boolean = false,
    private val fixedInputSize: Int = 224,
    useBasicGraphOpt: Boolean = false
) : ClassificationBackend {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val runner = OrtRunner(environment, modelFile, useXnnpack, useBasicGraphOpt)

    override fun run(inputBitmap: Bitmap): FloatArray {
        val working = Bitmap.createScaledBitmap(inputBitmap, fixedInputSize, fixedInputSize, true)
        return try {
            val tensorBuffer = bitmapToNormalizedFloatBuffer(working)
            val shape = longArrayOf(1, 3, working.height.toLong(), working.width.toLong())
            runner.run(tensorBuffer, shape)
        } finally {
            if (working !== inputBitmap) {
                working.recycle()
            }
        }
    }

    override fun close() {
        runner.close()
    }
}

private fun createOnnxSessionOptions(
    useXnnpack: Boolean,
    optLevel: OrtSession.SessionOptions.OptLevel
): OrtSession.SessionOptions {
    return OrtSession.SessionOptions().apply {
        val level = if (useXnnpack) {
            OrtSession.SessionOptions.OptLevel.BASIC_OPT
        } else {
            optLevel
        }
        setOptimizationLevel(level)
        if (useXnnpack) {
            try {
                addXnnpack(mapOf("intra_op_num_threads" to "4"))
                setIntraOpNumThreads(1)
                Log.i(ONNX_TAG, "XNNPACK execution provider enabled")
            } catch (e: Exception) {
                Log.w(ONNX_TAG, "XNNPACK unavailable at init: ${e.message}")
            }
        }
    }
}

private fun bitmapToNormalizedFloatBuffer(bitmap: Bitmap): FloatBuffer {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val buffer = ByteBuffer
        .allocateDirect(width * height * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    for (channel in 0 until 3) {
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val raw = when (channel) {
                0 -> Color.red(pixel)
                1 -> Color.green(pixel)
                else -> Color.blue(pixel)
            } / 255.0f
            buffer.put((raw - mean[channel]) / std[channel])
        }
    }

    buffer.rewind()
    return buffer
}

private fun flattenToFloatArray(value: Any?): FloatArray {
    val output = ArrayList<Float>()

    fun visit(node: Any?) {
        when (node) {
            null -> Unit
            is FloatArray -> node.forEach { output.add(it) }
            is DoubleArray -> node.forEach { output.add(it.toFloat()) }
            is IntArray -> node.forEach { output.add(it.toFloat()) }
            is LongArray -> node.forEach { output.add(it.toFloat()) }
            is Array<*> -> node.forEach { visit(it) }
            is Number -> output.add(node.toFloat())
            else -> throw IllegalArgumentException("Unsupported ONNX output type: ${node::class.java.name}")
        }
    }

    visit(value)
    return output.toFloatArray()
}
