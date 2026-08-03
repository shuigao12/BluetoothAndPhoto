package com.rokid.cxrmsamples.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rokid.cxrmsamples.activities.model.OnnxStageBackend
import com.rokid.cxrmsamples.activities.model.PtlStageBackend
import com.rokid.cxrmsamples.activities.model.SegmentationEngine
import com.rokid.cxrmsamples.activities.model.StageBackend
import com.rokid.cxrmsamples.activities.model.StageLayout
import com.rokid.cxrmsamples.activities.model.TwoStageSegmentationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SegmentationRepository(private val context: Context) {
    private val TAG = "SegmentationRepo"

    private val MODEL_ASSET_DIR = "model"

    private val initMutex = Mutex()
    private var segmentationEngine: SegmentationEngine? = null
    private var selectedStage1Id: String = Stage1ModelCatalog.defaultId
    private var selectedStage2Id: String = Stage2SegModelCatalog.defaultId

    var lastLoadError: String? = null
        private set

    fun getAvailableStage1Options(): List<Stage1ModelOption> {
        return Stage1ModelCatalog.allOptions.filter { isAssetAvailable(it.assetFileName) }
    }

    fun getSelectedStage1Option(): Stage1ModelOption? {
        return Stage1ModelCatalog.findById(selectedStage1Id)
            ?: Stage1ModelCatalog.allOptions.firstOrNull { isAssetAvailable(it.assetFileName) }
    }

    fun setSelectedStage1(optionId: String) {
        if (selectedStage1Id != optionId) {
            selectedStage1Id = optionId
            invalidateEngine()
            Log.i(TAG, "Stage1 selection changed to: $optionId")
        }
    }

    fun getAvailableStage2Options(): List<Stage2SegModelOption> {
        return Stage2SegModelCatalog.allOptions.filter { isAssetAvailable(it.assetFileName) }
    }

    fun getSelectedStage2Option(): Stage2SegModelOption? {
        return Stage2SegModelCatalog.findById(selectedStage2Id)
            ?: Stage2SegModelCatalog.allOptions.firstOrNull { isAssetAvailable(it.assetFileName) }
    }

    fun setSelectedStage2(optionId: String) {
        if (selectedStage2Id != optionId) {
            selectedStage2Id = optionId
            invalidateEngine()
            Log.i(TAG, "Stage2 selection changed to: $optionId")
        }
    }

    fun invalidateEngine() {
        segmentationEngine = null
    }

    suspend fun getOrInitEngine(onProgress: ((String) -> Unit)? = null): SegmentationEngine? = withContext(Dispatchers.IO) {
        segmentationEngine?.let {
            onProgress?.invoke("Models ready")
            return@withContext it
        }

        initMutex.withLock {
            segmentationEngine?.let {
                onProgress?.invoke("Models ready")
                return@withLock it
            }

            lastLoadError = null
            try {
                writeMarker("model_load_started.txt", "start:${System.currentTimeMillis()}")

                val stage1Option = getSelectedStage1Option()
                    ?: throw IOException("Stage 1 XNNPACK INT8 model is missing from assets/model")
                val stage2Option = getSelectedStage2Option()
                    ?: throw IOException("Stage 2 XNNPACK INT8 model is missing from assets/model")

                onProgress?.invoke("Loading ${stage1Option.displayName}...")
                Log.i(
                    TAG,
                    "Loading two-stage engine stage1=${stage1Option.assetFileName}, stage2=${stage2Option.assetFileName}"
                )

                segmentationEngine = createTwoStageEngine(stage1Option, stage2Option, onProgress)

                Log.i(TAG, "Segmentation engine created: ${segmentationEngine?.javaClass?.simpleName}")

                onProgress?.invoke("Warming up models...")
                try {
                    segmentationEngine?.warmup()
                } catch (e: Exception) {
                    Log.w(TAG, "Engine warmup failed: ${e.message}")
                }

                onProgress?.invoke("Models loaded")
                writeMarker("model_load_finished.txt", "done:${System.currentTimeMillis()}")
                segmentationEngine
            } catch (e: Exception) {
                lastLoadError = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Failed to load models: $lastLoadError", e)
                onProgress?.invoke("Model loading failed: $lastLoadError")
                writeMarker("model_load_failed.txt", "fail:${System.currentTimeMillis()}:$lastLoadError")
                null
            }
        }
    }

    private fun createTwoStageEngine(
        stage1Option: Stage1ModelOption,
        stage2Option: Stage2SegModelOption,
        onProgress: ((String) -> Unit)?
    ): SegmentationEngine {
        onProgress?.invoke("Loading Stage 1: ${stage1Option.assetFileName}...")
        onProgress?.invoke("Loading Stage 2: ${stage2Option.assetFileName}...")

        val stage1File = copyAssetToFile("$MODEL_ASSET_DIR/${stage1Option.assetFileName}", stage1Option.assetFileName)
        val stage2File = copyAssetToFile("$MODEL_ASSET_DIR/${stage2Option.assetFileName}", stage2Option.assetFileName)

        val stage1Backend = openStageBackend(
            stage1File,
            stage1Option.runtime,
            stage1Option.useXnnpack,
            STAGE1_INPUT_SIZE,
            stage1Option.useBasicGraphOpt
        )
        val stage2Backend = try {
            openStageBackend(
                stage2File,
                stage2Option.runtime,
                stage2Option.useXnnpack,
                STAGE2_INPUT_SIZE,
                stage2Option.useBasicGraphOpt
            )
        } catch (e: Exception) {
            stage1Backend.close()
            throw IOException("Stage 2 failed to load: ${e.message}", e)
        }
        return TwoStageSegmentationEngine(
            stage1Backend = stage1Backend,
            stage2Backend = stage2Backend,
            stage1Layout = StageLayout(classCount = 2, healthyIdx = 1, bgIdx = 0),
            stage2Layout = StageLayout(classCount = 3, healthyIdx = 1, bgIdx = 0, redIdx = 2),
            engineName = "two-stage-${stage1Option.id}-${stage2Option.id}"
        )
    }

    private fun openStageBackend(
        modelFile: File,
        runtime: ModelRuntime,
        useXnnpack: Boolean,
        inputSize: Int,
        useBasicGraphOpt: Boolean
    ): StageBackend {
        return when (runtime) {
            ModelRuntime.PTL -> PtlStageBackend(modelFile, inputSize)
            ModelRuntime.ONNX -> OnnxStageBackend(
                modelFile = modelFile,
                useXnnpack = useXnnpack,
                fixedInputSize = inputSize,
                useBasicGraphOpt = useBasicGraphOpt
            )
        }
    }

    private fun isAssetAvailable(fileName: String): Boolean {
        val assetManager = context.assets
        val paths = listOf("$MODEL_ASSET_DIR/$fileName", fileName)
        for (path in paths) {
            try {
                assetManager.open(path).close()
                return true
            } catch (_: IOException) {
            }
        }
        return false
    }

    private fun copyAssetToFile(assetPath: String, outFileName: String): File {
        val assetManager = context.assets
        val outFile = File(context.filesDir, outFileName)
        val assetSize = assetByteSize(assetPath).takeIf { it > 0L }
            ?: assetByteSize(outFileName).takeIf { it > 0L }

        val needsCopy = !outFile.exists() ||
            outFile.length() <= 0L ||
            (assetSize != null && outFile.length() != assetSize)

        if (needsCopy) {
            if (copyFromAssetPath(assetManager, assetPath, outFile)) {
                verifyCopied(outFile, assetSize)
                return outFile
            }
            if (copyFromAssetPath(assetManager, outFileName, outFile)) {
                verifyCopied(outFile, assetSize)
                return outFile
            }
            throw IOException("Asset not found: tried $assetPath and $outFileName")
        }

        Log.d(TAG, "Using existing model file: ${outFile.absolutePath} (${outFile.length() / 1024 / 1024}MB)")
        return outFile
    }

    private fun verifyCopied(outFile: File, assetSize: Long?) {
        if (assetSize != null && outFile.length() != assetSize) {
            throw IOException("Model size mismatch: ${outFile.name} (local ${outFile.length()} vs assets $assetSize)")
        }
    }

    private fun assetByteSize(assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun copyFromAssetPath(assetManager: android.content.res.AssetManager, path: String, outFile: File): Boolean {
        return try {
            assetManager.open(path).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied asset $path -> ${outFile.name} (${outFile.length() / 1024 / 1024}MB)")
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun writeMarker(filename: String, content: String) {
        try {
            File(context.filesDir, filename).writeText(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write marker $filename: ${e.message}")
        }
    }

    suspend fun runSegmentation(
        bitmap: Bitmap,
        onProgress: ((String) -> Unit)? = null
    ): com.rokid.cxrmsamples.activities.model.ImageSegmentationEngine.SegmentationResult? = withContext(Dispatchers.Default) {
        onProgress?.invoke("Initializing inference engine...")
        val engine = segmentationEngine ?: getOrInitEngine(onProgress)
        if (engine == null) {
            onProgress?.invoke("Inference engine unavailable: ${lastLoadError ?: "Unknown error"}")
            return@withContext null
        }
        onProgress?.invoke("Running inference (${bitmap.width}x${bitmap.height})...")
        engine.run(bitmap, onProgress ?: {})
    }

    companion object {
        private const val STAGE1_INPUT_SIZE = 512
        private const val STAGE2_INPUT_SIZE = 512
    }
}
