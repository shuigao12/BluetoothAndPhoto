package com.rokid.cxrmsamples.activities.picture

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Size
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rokid.cxrmsamples.comm.GlassesNotify
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import com.rokid.cxrmsamples.comm.PictureController
import com.rokid.cxrmsamples.comm.PictureVoiceController
import com.rokid.cxrmsamples.repository.SegmentationRepository
import com.rokid.cxrmsamples.repository.ImageStorageRepository
import com.rokid.cxrmsamples.repository.Stage1ModelOption
import com.rokid.cxrmsamples.repository.Stage2SegModelOption
import java.util.Collections
import java.io.FileOutputStream

// alias to the engine result type for convenience
private typealias SegmentationResult = com.rokid.cxrmsamples.activities.model.ImageSegmentationEngine.SegmentationResult

data class SessionImage(
    val timestamp: Long,
    val bitmap: ImageBitmap,
    val result: SegmentationResult? = null,
    val isProcessed: Boolean = false,
    val name: String = "", // generated name
    val errorMessage: String? = null,
    val timingInfo: String? = null,
    val pendingImagePath: String? = null,
    val modelName: String? = null
)

data class StageTiming(
    val stage: String,
    val durationMs: Long
)

data class ProcessingProgress(
    val step: String,
    val detail: String = "",
    val elapsedMs: Long = 0L,
    val stageTimings: List<StageTiming> = emptyList(),
    val isError: Boolean = false
)

/**
 * ViewModel class for handling picture capturing functionality.
 * Manages the state of picture taking, image display, and camera settings.
 */
class PictureViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PictureViewModel"

    private val segmentationRepository = SegmentationRepository(application)
    private val imageStorageRepository = ImageStorageRepository(application)

    // Data class for processing task
    private data class ProcessingTask(
        val imagePath: String,
        val timestamp: Long
    )

    // Channel for sequential processing
    private val processingChannel = Channel<ProcessingTask>(capacity = 5)

    // 记录最近一次拍照请求发起的时间（毫秒）
    private var lastCaptureRequestAtMs: Long = 0L
    private var lastClickAtMs: Long = 0L
    private val minCaptureIntervalMs = 800L

    // 防抖 + 取消分割任务
    private var segmentationJob: Job? = null

    // 状态提示（用于 Toast/Snackbar/状态文本）
    private val _captureStatus = MutableStateFlow<String?>(null)
    val captureStatus = _captureStatus.asStateFlow()

    private val _processingProgress = MutableStateFlow<ProcessingProgress?>(null)
    val processingProgress = _processingProgress.asStateFlow()

    private val _modelLoadState = MutableStateFlow("Not started")
    val modelLoadState = _modelLoadState.asStateFlow()

    private val _stage1Options = MutableStateFlow<List<Stage1ModelOption>>(emptyList())
    val stage1Options = _stage1Options.asStateFlow()

    private val _selectedStage1 = MutableStateFlow<Stage1ModelOption?>(null)
    val selectedStage1 = _selectedStage1.asStateFlow()

    private val _stage2Options = MutableStateFlow<List<Stage2SegModelOption>>(emptyList())
    val stage2Options = _stage2Options.asStateFlow()

    private val _selectedStage2 = MutableStateFlow<Stage2SegModelOption?>(null)
    val selectedStage2 = _selectedStage2.asStateFlow()

    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading = _isModelLoading.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private var progressStartMs: Long = 0L
    private val currentStageTimings = mutableListOf<StageTiming>()

    // 自动保存开关
    private val _isAutoSave = MutableStateFlow(true)
    val isAutoSave = _isAutoSave.asStateFlow()

    // 连续拍摄会话的图片列表
    private val _sessionImages = MutableStateFlow<List<SessionImage>>(emptyList())
    val sessionImages = _sessionImages.asStateFlow()

    // 当前选中的图片（用于UI详情展示/手动保存）
    private val _currentSessionImage = MutableStateFlow<SessionImage?>(null)
    val currentSessionImage = _currentSessionImage.asStateFlow()

    /**
     * Array of available picture sizes for capturing images.
     * The camera on the Glasses has been rotated by 90°, so in this context,
     * the [Size.getWidth] from [androidx.compose.ui.geometry.Size] represents the actual image's height,
     * while [Size.getHeight] represents the actual image's width.
     */
    val pictureSize: Array<Size> = arrayOf(
        Size(1280, 720),
        Size(1920, 1080),
        Size(3024, 3632),
        Size(1600, 1200),
        Size(1024, 768),
        Size(960, 540),
        Size(854, 480),
        Size(640, 480)
    )

    /** State flow indicating if a photo is currently being taken */
    private val _takingPhoto = MutableStateFlow(false)
    val takingPhoto = _takingPhoto.asStateFlow()

    /** State flow holding the currently selected picture size */
    private val _selectedPictureSize = MutableStateFlow(pictureSize[0])
    val selectedPictureSize = _selectedPictureSize.asStateFlow()

    /** State flow holding the captured image as ImageBitmap for display */
    private val _showImageBitmap: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    val showImageBitmap = _showImageBitmap.asStateFlow()

    // UI state + segmentation result flows
    private val _segmentationResult = MutableStateFlow<SegmentationResult?>(null)
    val segmentationResult = _segmentationResult.asStateFlow()

    private val _uiState = MutableStateFlow<PictureUiState>(PictureUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // New: expose photo params set status to UI so Compose can show a toast/snackbar
    // Value: null = no recent status, otherwise a localized short message
    private val _photoParamsStatus = MutableStateFlow<String?>(null)
    val photoParamsStatus = _photoParamsStatus.asStateFlow()

    // Save result status for UI feedback (Toast/Snackbar)
    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus = _saveStatus.asStateFlow()

    /** PictureActivity 前台时的语音拍照状态提示 */
    private val _voiceStatus = MutableStateFlow("")
    val voiceStatus = _voiceStatus.asStateFlow()

    private var pictureVoiceController: PictureVoiceController? = null

    private val _defaultNameState = MutableStateFlow("")
    val defaultNameState = _defaultNameState.asStateFlow()

    val canStartAnalysis = combine(
        _currentSessionImage,
        _isAnalyzing,
        _isModelReady,
        _isModelLoading
    ) { image: SessionImage?, analyzing: Boolean, ready: Boolean, loading: Boolean ->
        image != null &&
            !image.isProcessed &&
            image.pendingImagePath != null &&
            !analyzing &&
            ready &&
            !loading
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var originalBitmap: Bitmap? = null

    // REMOVED: Model loading and segmentation logic moved to SegmentationRepository
    // private val MODEL_ASSET_DIR = "model"
    // private var segmentationEngine: ...

    init {
        Log.i(TAG, "PictureViewModel initialized (models not loaded yet).")
        // Do not load models automatically here; call loadModels() from the Activity when ready.

        // Register remote take callback so that simple incoming commands can trigger takePicture()
        PictureController.onRemoteTake = {
            // Ensure we call into ViewModel's logic on the main thread via viewModelScope
            viewModelScope.launch {
                try {
                    handleRemoteTake()
                } catch (t: Throwable) {
                    Log.w(TAG, "remote take handler failed: ${t.message}")
                }
            }
        }

        // Attempt to set photo params at startup so device has sane defaults if remote take arrives early
        try {
            Log.i(TAG, "init: attempting to set photo params to ${_selectedPictureSize.value.width}x${_selectedPictureSize.value.height}")
            setPhotoParams()
        } catch (t: Throwable) {
            Log.w(TAG, "init: setPhotoParams() threw: ${t.message}")
        }

        // Start storage consumer
        viewModelScope.launch {
            for (task in processingChannel) {
                processTask(task)
            }
        }
    }

    private suspend fun processTask(task: ProcessingTask) {
        _isAnalyzing.value = true
        try {
            processTaskInternal(task)
        } finally {
            _isAnalyzing.value = false
            // 分析结束后刷新当前选中项 UI，避免仍停留在 Loading 态
            _currentSessionImage.value?.let { selectSessionImage(it) }
        }
    }

    private suspend fun processTaskInternal(task: ProcessingTask) {
        progressStartMs = System.currentTimeMillis()
        currentStageTimings.clear()
        reportProgress("1/6 Decode image", "Reading cached image...")

        val decodeStart = System.currentTimeMillis()
        val bitmap = try {
            BitmapFactory.decodeFile(task.imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode cached file: ${task.imagePath}", e)
            null
        }
        recordStageSince("Decode image", decodeStart)

        // Always delete temp file after decoding to save space
        try {
            File(task.imagePath).delete()
        } catch (_: Exception) {
            Log.w(TAG, "Failed to delete temp file: ${task.imagePath}")
        }

        if (bitmap == null) {
            failTask(task, "Image decoding failed")
            return
        }

        try {
            val scaleStart = System.currentTimeMillis()
            val inferenceBitmap = scaleDownForInference(bitmap)
            if (inferenceBitmap !== bitmap) {
                recordStageSince("Resize image", scaleStart)
                reportProgress("1/6 Resize image", "${bitmap.width}x${bitmap.height} → ${inferenceBitmap.width}x${inferenceBitmap.height}")
            } else {
                reportProgress("1/6 Image ready", "${bitmap.width}x${bitmap.height}")
            }

            reportProgress("2/6 Run inference", "${inferenceBitmap.width}x${inferenceBitmap.height}")

            val inferStart = System.currentTimeMillis()
            val result = segmentationRepository.runSegmentation(inferenceBitmap) { detail ->
                reportProgress("2/6 Run inference", detail)
            }
            recordStageSince("Inference total", inferStart)
            if (inferenceBitmap !== bitmap) {
                inferenceBitmap.recycle()
            }
            if (result == null) {
                Log.w(TAG, "Segmentation failed for task ${task.timestamp}")
                val detail = segmentationRepository.lastLoadError
                failTask(
                    task,
                    detail?.let { "Analysis failed: $it" }
                        ?: "Analysis failed: no leaf was detected or inference returned an error"
                )
                return
            }

            recordStage("Stage 1 coarse segmentation", result.stage1RuntimeMs)
            recordStage("Stage 2 fine segmentation", result.stage2RuntimeMs)

            val nameStart = System.currentTimeMillis()
            reportProgress(
                "3/6 Generate name",
                "affected area=${String.format(Locale.US, "%.2f%%", result.percent * 100f)}"
            )

            val baseName = _defaultNameState.value.trim()
            val finalName = if (baseName.isEmpty()) {
                 SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(task.timestamp))
            } else {
                 generateIndexedName(baseName)
            }

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(task.timestamp))
            recordStageSince("Generate name", nameStart)

            val timingInfo = buildTimingInfo(result)
            val modelName = currentModelName()

            var success = false
            if (_isAutoSave.value) {
                val saveStart = System.currentTimeMillis()
                reportProgress("4/6 Auto-save", finalName)
                Log.i(TAG, "Auto-saving: finalName=$finalName, date=$dateStr, model=$modelName, timing=$timingInfo")
                success = imageStorageRepository.saveSegmentationResult(
                    bitmap, result.percent, finalName, dateStr, timingInfo, modelName
                )
                recordStageSince("Auto-save", saveStart)
                if (success) {
                    _captureStatus.value = "Saved automatically: $finalName"
                } else {
                    Log.w(TAG, "Auto-save failed for $finalName")
                }
            } else {
                Log.i(TAG, "Auto-save disabled. Processing finished for: $finalName")
                _captureStatus.value = "Analysis complete (not saved): $finalName"
            }

            val drawStart = System.currentTimeMillis()
            reportProgress("5/6 Render result", timingSummary(result))
            val annotatedBitmap = try {
                drawResultOnBitmap(bitmap, result.percent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to draw overlay", e)
                bitmap
            }
            recordStageSince("Render overlay", drawStart)

            sendSegmentationResultToGlass(result.percent)

            reportProgress("6/6 Complete", "Total ${System.currentTimeMillis() - progressStartMs}ms")
            updateSessionImage(task.timestamp, annotatedBitmap.asImageBitmap(), result, finalName, timingInfo, modelName)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing task", e)
            failTask(task, "Processing error: ${e.message}")
        }
    }

    private fun recordStageSince(stage: String, startMs: Long) {
        recordStage(stage, System.currentTimeMillis() - startMs)
    }

    private fun recordStage(stage: String, durationMs: Long) {
        if (durationMs < 0) return
        currentStageTimings.add(StageTiming(stage, durationMs))
        Log.i(TAG, "[StageTiming] $stage = ${durationMs}ms")
    }

    private fun currentModelName(): String {
        val s1 = _selectedStage1.value?.displayName ?: "Unknown"
        val s2 = _selectedStage2.value?.displayName ?: "Unknown"
        return "$s1 / $s2"
    }

    private fun buildTimingInfo(result: SegmentationResult): String {
        return currentStageTimings.joinToString("; ") { "${it.stage}=${it.durationMs}ms" } +
            "; Inference total=${result.runtimeMs}ms"
    }

    private fun timingSummary(result: SegmentationResult): String {
        return "Stage 1=${result.stage1RuntimeMs}ms, Stage 2=${result.stage2RuntimeMs}ms, total=${result.runtimeMs}ms"
    }

    private fun reportProgress(step: String, detail: String = "") {
        val elapsed = System.currentTimeMillis() - progressStartMs
        val progress = ProcessingProgress(step, detail, elapsed, currentStageTimings.toList())
        _processingProgress.value = progress
        _captureStatus.value = if (detail.isNotEmpty()) "$step: $detail" else step
        Log.i(TAG, "[Progress] $step | $detail | ${elapsed}ms")
    }

    private fun failTask(task: ProcessingTask, message: String) {
        val elapsed = System.currentTimeMillis() - progressStartMs
        _processingProgress.value = ProcessingProgress("Failed", message, elapsed, currentStageTimings.toList(), isError = true)
        _captureStatus.value = message
        _uiState.value = PictureUiState.Error(message)
        Log.e(TAG, "[Progress] FAILED | $message | ${elapsed}ms")

        val currentList = _sessionImages.value.toMutableList()
        val index = currentList.indexOfFirst { it.timestamp == task.timestamp }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                isProcessed = false,
                errorMessage = message
            )
            _sessionImages.value = currentList
            if (_currentSessionImage.value?.timestamp == task.timestamp) {
                _currentSessionImage.value = currentList[index]
            }
        }
    }

    // 更新列表中的某张图片状态
    private fun updateSessionImage(
        ts: Long,
        newBitmap: ImageBitmap,
        res: SegmentationResult,
        savedName: String,
        timingInfo: String? = null,
        modelName: String? = null
    ) {
        val currentList = _sessionImages.value.toMutableList()
        val index = currentList.indexOfFirst { it.timestamp == ts }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                bitmap = newBitmap,
                result = res,
                isProcessed = true,
                name = savedName,
                timingInfo = timingInfo,
                modelName = modelName,
                pendingImagePath = null,
                errorMessage = null
            )
            _sessionImages.value = currentList

            // 如果当前选中的就是这张（或者当前还没有选中），更新选中状态
            // 简单的逻辑：如果用户盯着这张图，刷新它
            if (_currentSessionImage.value?.timestamp == ts) {
                _currentSessionImage.value = currentList[index]
                // 同步更新UI需要的通用状态
                _segmentationResult.value = res
                _uiState.value = PictureUiState.SegmentationFinished(res)
            }
        }
    }

    // 供UI切换选中图片
    fun selectSessionImage(image: SessionImage) {
        _currentSessionImage.value = image
        if (image.result != null) {
            _segmentationResult.value = image.result
            _uiState.value = PictureUiState.SegmentationFinished(image.result)
        } else if (image.errorMessage != null) {
            _segmentationResult.value = null
            _uiState.value = PictureUiState.Error(image.errorMessage)
        } else if (image.pendingImagePath != null && !image.isProcessed) {
            _segmentationResult.value = null
            _uiState.value = PictureUiState.ReadyToAnalyze
        } else if (_isAnalyzing.value) {
            _segmentationResult.value = null
            _uiState.value = PictureUiState.Loading
        } else {
            _segmentationResult.value = null
            _uiState.value = PictureUiState.Idle
        }
    }

    fun startAnalysis() {
        val image = _currentSessionImage.value ?: return
        val path = image.pendingImagePath ?: return
        if (!_isModelReady.value) {
            _captureStatus.value = "Models are still loading"
            return
        }
        if (_isAnalyzing.value || _isModelLoading.value) return

        val task = ProcessingTask(path, image.timestamp)
        val delivered = processingChannel.trySend(task).isSuccess
        if (!delivered) {
            _captureStatus.value = "The processing queue is full. Try again shortly."
            _uiState.value = PictureUiState.Error("Processing queue full")
            return
        }

        progressStartMs = System.currentTimeMillis()
        currentStageTimings.clear()
        _uiState.value = PictureUiState.Loading
        _captureStatus.value = "Analysis started..."
        Log.i(TAG, "startAnalysis(): enqueued task ts=${image.timestamp}")
    }

    fun updateDefaultName(name: String) {
        _defaultNameState.value = name
    }

    fun setAutoSave(enabled: Boolean) {
        _isAutoSave.value = enabled
    }

    // Helper to generate non-duplicate name based on DB is expensive if we do too many queries.
    // Ideally maintain an in-memory counter or query max index?
    // Or simply try: Name_001, Name_002... check DB each time.
    private suspend fun generateIndexedName(baseName: String): String {
        return withContext(Dispatchers.IO) {
            // Find the next available index.
            // Naive loop might be slow if there are many files.
            // A better way is to query max index, but for now loop is safe.
            var index = 1
            var candidate = String.format(Locale.US, "%s_%03d", baseName, index)
            val db = com.rokid.cxrmsamples.data.PicDBHelper.getInstance(getApplication())

            // Loop until we find a name that doesn't exist
            while (db.queryByName(candidate) != null) {
                index++
                candidate = String.format(Locale.US, "%s_%03d", baseName, index)
                // Safety break to prevent infinite loop
                if (index > 10000) {
                     candidate = "${baseName}_${System.currentTimeMillis()}"
                     break
                }
            }
            candidate
        }
    }

    /**
     * Public method to trigger asynchronous model copying/loading. Safe to call multiple times.
     */
    fun loadModels() {
        Log.i(TAG, "loadModels(): triggering repository init")
        viewModelScope.launch {
            refreshModelOptions()
            reloadModelsInternal()
        }
    }

    private suspend fun refreshModelOptions() {
        val stage1 = segmentationRepository.getSelectedStage1Option()
        val stage2 = segmentationRepository.getSelectedStage2Option()
        _selectedStage1.value = stage1
        _selectedStage2.value = stage2
        _stage1Options.value = listOfNotNull(stage1)
        _stage2Options.value = listOfNotNull(stage2)
    }

    private suspend fun reloadModelsInternal() {
        segmentationRepository.invalidateEngine()
        progressStartMs = System.currentTimeMillis()
        _isModelLoading.value = true
        _isModelReady.value = false
        _modelLoadState.value = "Loading..."
        reportProgress("Model loading", "Preparing XNNPACK INT8 model files")
        val engine = segmentationRepository.getOrInitEngine { detail ->
            _modelLoadState.value = detail
            reportProgress("Model loading", detail)
        }
        _modelLoadState.value = if (engine != null) {
            "Ready · XNNPACK INT8"
        } else {
            "Load failed: ${segmentationRepository.lastLoadError ?: "Unknown error"}"
        }
        _isModelLoading.value = false
        _isModelReady.value = engine != null
        if (engine == null) {
            _processingProgress.value = ProcessingProgress(
                step = "Model loading failed",
                detail = "Verify the XNNPACK INT8 files in assets/model",
                isError = true
            )
        }
    }

    private fun scaleDownForInference(bitmap: Bitmap, maxSide: Int = 1280): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxSide) return bitmap
        val scale = maxSide.toFloat() / maxDim
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        Log.i(TAG, "Scaling image for inference: ${bitmap.width}x${bitmap.height} -> ${newW}x${newH}")
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /** Draws the affected-area percentage in the top-right corner. */
    private fun drawResultOnBitmap(src: Bitmap, percent: Float): Bitmap {
        val mutable = try {
            src.copy(Bitmap.Config.ARGB_8888, true)
        } catch (_: Throwable) {
            null
        } ?: return src

        val canvas = Canvas(mutable)
        val app = getApplication<Application>()
        val dm = app.resources.displayMetrics
        val density = dm.density

        val percentText = String.format(Locale.US, "%.1f%%", percent * 100f)
        val lines = listOf(percentText)

        val minDim = min(mutable.width, mutable.height).toFloat()
        val textSizePx = max(minDim * 0.07f, 16f * density)
        val paddingPx = max(minDim * 0.015f, 6f * density)
        val lineGapPx = 4f * density
        val cornerPx = 6f * density

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            textSize = textSizePx
            style = Paint.Style.FILL
            setShadowLayer(2.5f * density, 0f, 0f, 0x88000000.toInt())
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAA000000.toInt()
            style = Paint.Style.FILL
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        val maxTextWidth = lines.maxOf { textPaint.measureText(it) }
        val right = mutable.width.toFloat() - paddingPx
        val left = max(paddingPx, right - maxTextWidth - paddingPx)
        val top = paddingPx
        val bottom = top + lineHeight * lines.size + lineGapPx * (lines.size - 1) + paddingPx

        val bgRect = RectF(left - paddingPx * 0.5f, top - paddingPx * 0.5f, right, bottom)
        canvas.drawRoundRect(bgRect, cornerPx, cornerPx, bgPaint)

        var textY = top - fontMetrics.top
        lines.forEach { line ->
            canvas.drawText(line, left, textY, textPaint)
            textY += lineHeight + lineGapPx
        }

        return mutable
    }

    /** Callback for handling the result of photo capture */
    private val pictureCallback = PhotoResultCallback { status, imageData ->
        val callbackAt = SystemClock.elapsedRealtime()
        val transferMs = if (lastCaptureRequestAtMs > 0L) (callbackAt - lastCaptureRequestAtMs) else -1L
        Log.i(TAG, "pictureCallback invoked on thread=${Thread.currentThread().name}, status=$status, imageData.size=${imageData?.size ?: 0}, transferMs=${if (transferMs >= 0) transferMs else null}")
        if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED && imageData != null && imageData.isNotEmpty() && transferMs >= 0) {
            val kb = imageData.size / 1024.0
            val sec = transferMs / 1000.0
            val kbps = if (sec > 0) kb / sec else 0.0
            Log.i(TAG, String.format(Locale.US, "capture_to_phone: size=%.1fKB, time=%dms, throughput=%.1fKB/s", kb, transferMs, kbps))
            _captureStatus.value = "Photo received in ${transferMs}ms"
        }
        _takingPhoto.value = false
        GlassesNotify.photoCaptureEnd()
        when (status) {
            ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                if (imageData == null || imageData.isEmpty()) {
                    Log.e(TAG, "Received empty image data; showing placeholder")
                    _captureStatus.value = "No image data received"
                    _showImageBitmap.value = null
                    _segmentationResult.value = null
                    _uiState.value = PictureUiState.Error("No image data received")
                    return@PhotoResultCallback
                }

                // 1. Cache to file to avoid OOM
                try {
                    val ts = System.currentTimeMillis()
                    val cacheDir = getApplication<Application>().cacheDir
                    val tempFile = File(cacheDir, "img_$ts.jpg")
                    // Use Kotlin's writeBytes to avoid IO stream imports
                    tempFile.writeBytes(imageData)

                    val bitmapRaw = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    val imageBitmap = bitmapRaw.asImageBitmap()

                    val newItem = SessionImage(
                        timestamp = ts,
                        bitmap = imageBitmap,
                        isProcessed = false,
                        pendingImagePath = tempFile.absolutePath
                    )
                    val newList = _sessionImages.value.toMutableList()
                    newList.add(newItem)
                    _sessionImages.value = newList

                    selectSessionImage(newItem)
                    _showImageBitmap.value = imageBitmap
                    _captureStatus.value = "Photo ready. Tap Analyze."

                } catch (e: Exception) {
                    Log.e(TAG, "Error caching image", e)
                     _captureStatus.value = "Cache error: ${e.message}"
                }
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                _captureStatus.value = "The device is busy. Try again shortly."
                _uiState.value = PictureUiState.Error("Device busy")
                Log.w(TAG, "pictureCallback status=$status, no image")
            }
            else -> {
                Log.w(TAG, "pictureCallback status=$status, no image")
                _captureStatus.value = "Capture failed (status=$status)"
                _uiState.value = PictureUiState.Error("Capture failed (status=$status)")
            }
        }
    }

    /**
     * Capture a picture with the currently selected size.
     * Uses the CXR API to take a photo with the glass camera.
     */
    fun takePicture() {
        Log.i(TAG, "takePicture() called on thread=${Thread.currentThread().name}")
        val now = SystemClock.elapsedRealtime()
        if (_takingPhoto.value) {
            _captureStatus.value = "Capture already in progress"
            Log.w(TAG, "takePicture ignored: takingPhoto is true")
            return
        }
        if (now - lastClickAtMs < minCaptureIntervalMs) {
            _captureStatus.value = "Please wait before taking another photo"
            Log.w(TAG, "takePicture ignored: interval too short (${now - lastClickAtMs}ms < $minCaptureIntervalMs)")
            return
        }
        lastClickAtMs = now

        // 清空上一次的结果，保证每次点击拍摄按钮都是新的状态
        _showImageBitmap.value = null
        _segmentationResult.value = null
        originalBitmap = null
        // Do NOT set Loading state here for queue logic, or set a "Queuing" state
        // _uiState.value = PictureUiState.Loading
        _captureStatus.value = "Taking photo..."

        GlassesNotify.photoCaptureStart()
        _takingPhoto.value = true
        lastCaptureRequestAtMs = SystemClock.elapsedRealtime()
        val size = _selectedPictureSize.value
        try {
            val ret = CxrApi.getInstance().takeGlassPhotoGlobal(size.width, size.height, 100, pictureCallback)
            Log.i(TAG, "takeGlassPhotoGlobal returned: $ret for ${size.width}x${size.height}")
            when (ret) {
                ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                    Log.i(TAG, "takeGlassPhotoGlobal REQUEST_SUCCEED")
                }
                ValueUtil.CxrStatus.REQUEST_FAILED -> {
                    Log.e(TAG, "takeGlassPhotoGlobal REQUEST_FAILED")
                    _takingPhoto.value = false
                    GlassesNotify.photoCaptureEnd()
                    _captureStatus.value = "Capture request failed"
                    _uiState.value = PictureUiState.Error("Capture request failed")
                }
                ValueUtil.CxrStatus.REQUEST_WAITING -> {
                    Log.w(TAG, "takeGlassPhotoGlobal REQUEST_WAITING")
                    _captureStatus.value = "Device busy. Retrying automatically."
                }
                else -> {
                    Log.e(TAG, "takeGlassPhotoGlobal unknown status $ret")
                    _takingPhoto.value = false
                    GlassesNotify.photoCaptureEnd()
                    _captureStatus.value = "Unexpected capture response"
                    _uiState.value = PictureUiState.Error("Unexpected capture response")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "takeGlassPhotoGlobal threw: ${t.message}", t)
            _takingPhoto.value = false
            GlassesNotify.photoCaptureEnd()
            _captureStatus.value = "Capture error: ${t.message}"
            _uiState.value = PictureUiState.Error("Capture error: ${t.message}")
        }
    }

    fun importLocalImage(uri: Uri) {
        Log.i(TAG, "importLocalImage() called with uri=$uri")
        viewModelScope.launch {
            _captureStatus.value = "Importing image..."

            val bitmap = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to decode local image: ${t.message}", t)
                null
            }

            if (bitmap == null) {
                _captureStatus.value = "Unable to read the local image"
                _uiState.value = PictureUiState.Error("Unable to read the local image")
                return@launch
            }

            val ts = System.currentTimeMillis()
            val displayName = buildLocalImageName(uri, ts)

            try {
                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "local_img_$ts.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile).use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                            throw IllegalStateException("Bitmap compress returned false")
                        }
                        output.flush()
                    }
                }

                val imageBitmap = bitmap.asImageBitmap()
                val newItem = SessionImage(
                    timestamp = ts,
                    bitmap = imageBitmap,
                    isProcessed = false,
                    name = displayName,
                    pendingImagePath = tempFile.absolutePath
                )
                val newList = _sessionImages.value.toMutableList()
                newList.add(newItem)
                _sessionImages.value = newList
                selectSessionImage(newItem)
                _showImageBitmap.value = imageBitmap
                originalBitmap = bitmap
                _captureStatus.value = "Image imported. Tap Analyze."
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to import local image: ${t.message}", t)
                _captureStatus.value = "Image import failed: ${t.message}"
                _uiState.value = PictureUiState.Error("Image import failed")
            }
        }
    }

    private fun buildLocalImageName(uri: Uri, timestamp: Long): String {
        val resolver = getApplication<Application>().contentResolver
        val displayName = try {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        } catch (_: Throwable) {
            null
        }

        val baseName = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        return "local_$baseName"
    }

    fun saveResult(result: SegmentationResult?, name: String, date: String) {
        if (result == null) {
            Log.e(TAG, "Save failed: SegmentationResult is null.")
            _saveStatus.value = "Save failed: result is empty"
            viewModelScope.launch { delay(2000); _saveStatus.value = null }
            return
        }

        viewModelScope.launch {
            // Use current selected image bitmap if available, fallback to originalBitmap
            // Note: originalBitmap is only the LAST captured one. _currentSessionImage is what the user is looking at.
            val currentImg = _currentSessionImage.value
            val bmp = if (currentImg != null) {
                currentImg.bitmap.asAndroidBitmap()
            } else {
                originalBitmap
            }

            if (bmp == null) {
                 _saveStatus.value = "Save failed: image is no longer available"
                 delay(2000)
                 _saveStatus.value = null
                 return@launch
            }
            val timingInfo = _currentSessionImage.value?.timingInfo
            val modelName = _currentSessionImage.value?.modelName ?: currentModelName()
            val success = imageStorageRepository.saveSegmentationResult(
                bmp, result.percent, name, date, timingInfo, modelName
            )
            if (success) {
                _saveStatus.value = "Saved successfully"
            } else {
                // Determine reason if possible, or just check here again?
                // Actually repository doesn't return reason. Let's do a quick check here for better message or improve repo return.
                // For simplicity, let's query again just to give specific error message if name exists.
                val existing = withContext(Dispatchers.IO) {
                    com.rokid.cxrmsamples.data.PicDBHelper.getInstance(getApplication()).queryByName(name)
                }
                if (existing != null) {
                    _saveStatus.value = "Save failed: name already exists"
                } else {
                    _saveStatus.value = "Save failed"
                }
            }
             // 清理提示，避免长期占用
            delay(2500)
            _saveStatus.value = null
        }
    }

    /**
     * 选择分辨率并自动设置到设备
     */
    fun sizeChoose(resolution: Size) {
        _selectedPictureSize.value = resolution
        try {
            Log.i(TAG, "sizeChoose: selected ${resolution.width}x${resolution.height}, calling setPhotoParams() automatically")
            setPhotoParams()
        } catch (e: Exception) {
            Log.w(TAG, "sizeChoose: setPhotoParams() threw: ${e.message}")
        }
    }

    /**
     * 设置拍照参数到设备（宽高）
     */
    fun setPhotoParams() {
        val size = _selectedPictureSize.value
        val result = CxrApi.getInstance().setPhotoParams(size.width, size.height)
        when (result) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.i(TAG, "setPhotoParams: REQUEST_SUCCEED for ${size.width}x${size.height}")
                _photoParamsStatus.value = "Camera resolution updated"
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(TAG, "setPhotoParams: REQUEST_FAILED for ${size.width}x${size.height}")
                _photoParamsStatus.value = "Unable to update camera resolution"
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(TAG, "setPhotoParams: REQUEST_WAITING for ${size.width}x${size.height}")
                _photoParamsStatus.value = "Device not ready"
            }
            else -> {
                Log.e(TAG, "setPhotoParams: unknown result $result for ${size.width}x${size.height}")
                _photoParamsStatus.value = "Unknown camera setting response"
            }
        }

        viewModelScope.launch {
            delay(2000)
            _photoParamsStatus.value = null
        }
    }

    private fun sendSegmentationResultToGlass(percent: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            GlassesNotify.segmentationResult(percent)
        }
    }

    private fun handleRemoteTake() {
        // Mirror checks from takePicture() to avoid duplicate/rapid remote triggers
        val now = SystemClock.elapsedRealtime()
        if (_takingPhoto.value) {
            _captureStatus.value = "Remote capture ignored: capture already in progress"
            Log.i(TAG, "handleRemoteTake ignored: already taking photo")
            return
        }
        if (now - lastClickAtMs < minCaptureIntervalMs) {
            _captureStatus.value = "Remote capture ignored: please wait"
            Log.i(TAG, "handleRemoteTake debounced: interval too short")
            return
        }
        // update last click timestamp and invoke take
        // lastClickAtMs = now // REMOVED: Do not update timestamp here, let takePicture() handle it.
        // Otherwise takePicture() will see the updated timestamp and think it's a double-click!
        takePicture()
    }

    /** PictureActivity 进入前台且已授权麦克风时调用 */
    fun enableVoiceControl() {
        if (pictureVoiceController != null) return
        pictureVoiceController = PictureVoiceController(
            appContext = getApplication(),
            scope = viewModelScope,
            onTakePhoto = {
                viewModelScope.launch {
                    handleRemoteTake()
                }
            },
            onStatusChanged = { status ->
                _voiceStatus.value = status
            },
        ).also { it.start() }
    }

    /** PictureActivity 离开前台时调用 */
    fun disableVoiceControl() {
        pictureVoiceController?.stop()
        pictureVoiceController = null
        _voiceStatus.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        disableVoiceControl()
        // Clear remote callback to avoid leaking ViewModel
        PictureController.onRemoteTake = null
    }

    sealed class PictureUiState {
        object Idle : PictureUiState()
        object ReadyToAnalyze : PictureUiState()
        object Loading : PictureUiState()
        data class SegmentationFinished(val result: SegmentationResult) : PictureUiState()
        data class Error(val message: String) : PictureUiState()
    }
}
