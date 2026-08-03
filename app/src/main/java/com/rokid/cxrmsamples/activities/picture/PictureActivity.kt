package com.rokid.cxrmsamples.activities.picture

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.background
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight

/** 手动「保存结果」按钮；自动保存开启时可设为 false，保留对话框与 saveResult 逻辑供复用。 */
private const val SHOW_MANUAL_SAVE_BUTTON = false

class PictureActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IMPORT_LOCAL_IMAGE_URI = "extra_import_local_image_uri"
    }

    private val viewModel: PictureViewModel by viewModels()

    private val requestRecordAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.enableVoiceControl()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice capture", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importLocalImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("PictureActivity", "onCreate called")
        viewModel.loadModels() // Manually trigger model loading

        intent?.getStringExtra(EXTRA_IMPORT_LOCAL_IMAGE_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { viewModel.importLocalImage(Uri.parse(it)) }

        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PictureScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onImportLocalImage = {
                            pickImageLauncher.launch("image/*")
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureVoiceControl()
    }

    override fun onPause() {
        viewModel.disableVoiceControl()
        super.onPause()
    }

    private fun ensureVoiceControl() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.enableVoiceControl()
            else -> requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureScreen(
    modifier: Modifier = Modifier,
    viewModel: PictureViewModel,
    onImportLocalImage: () -> Unit
) {
    val selectedResolution by viewModel.selectedPictureSize.collectAsState()
    val isAutoSave by viewModel.isAutoSave.collectAsState()
    val imageBitmap by viewModel.showImageBitmap.collectAsState()
    val taking by viewModel.takingPhoto.collectAsState()
    val resolutions = viewModel.pictureSize
    val segResult by viewModel.segmentationResult.collectAsState()
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var defaultName by rememberSaveable { mutableStateOf("") }
    var dateInput by remember { mutableStateOf("") }

    // New: Observe session images list and current selection
    val sessionImages by viewModel.sessionImages.collectAsState()
    val currentImage by viewModel.currentSessionImage.collectAsState()
    var fullScreenImage by remember { mutableStateOf<SessionImage?>(null) }

    // Listen for photo params status from ViewModel and show a Toast when non-null
    val photoParamsStatus by viewModel.photoParamsStatus.collectAsState()
    LaunchedEffect(photoParamsStatus) {
        photoParamsStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    // New: listen for save status to surface errors/success
    val saveStatus by viewModel.saveStatus.collectAsState()
    LaunchedEffect(saveStatus) {
        saveStatus?.let { status ->
             if (status.contains("name already exists")) {
                 // 保存失败：名字已存在 -> 重新打开对话框让用户修改
                 showSaveDialog = true
                 Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
             } else {
                 Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
             }
        }
    }

    // 新增：监听捕获/处理状态，Toast 提示
    val captureStatus by viewModel.captureStatus.collectAsState()
    LaunchedEffect(captureStatus) {
        captureStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    val voiceStatus by viewModel.voiceStatus.collectAsState()

    // 新增：监听 UI 状态，用于叠加“图片处理中”提示
    val uiState by viewModel.uiState.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val modelLoadState by viewModel.modelLoadState.collectAsState()
    val isModelLoading by viewModel.isModelLoading.collectAsState()
    val isModelReady by viewModel.isModelReady.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val canStartAnalysis by viewModel.canStartAnalysis.collectAsState()

    // Sync pager state with current image selection
    val pagerState = rememberPagerState(pageCount = { sessionImages.size })

    // Effect: sync ViewModel selection -> Pager
    LaunchedEffect(currentImage) {
        val index = sessionImages.indexOfFirst { it.timestamp == currentImage?.timestamp }
        if (index != -1 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // Effect: sync Pager scroll -> ViewModel selection
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < sessionImages.size) {
            val image = sessionImages[pagerState.currentPage]
            if (image.timestamp != currentImage?.timestamp) {
                viewModel.selectSessionImage(image)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            modifier = Modifier.fillMaxSize(),
            contentDescription = null,
            alpha = 0.08f
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CaptureSettingsCard(
                modelLoadState = modelLoadState,
                resolutions = resolutions,
                selectedResolution = selectedResolution,
                onResolutionSelected = { viewModel.sizeChoose(it) },
                defaultName = defaultName,
                onDefaultNameChange = {
                    defaultName = it
                    viewModel.updateDefaultName(it)
                },
                isAutoSave = isAutoSave,
                onAutoSaveChange = { viewModel.setAutoSave(it) }
            )

            // 图片显示区域：固定高度 + Pager 支持左右滑动（避免 weight 挤占下方按钮空间）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sessionImages.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = sessionImages[page]
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .clickable { fullScreenImage = item }
                            ) {
                                Image(
                                    bitmap = item.bitmap,
                                    contentDescription = "Capture $page",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                // Overlay info for processing status
                                if (!item.isProcessed && item.pendingImagePath != null) {
                                    Text(
                                        "Ready to analyze",
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(
                                                Color(0xFF1565C0).copy(alpha = 0.85f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                            )
                                            .padding(4.dp),
                                        color = Color.White
                                    )
                                } else if (!item.isProcessed && item.errorMessage != null) {
                                    Text(
                                        "Analysis failed",
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(
                                                Color(0xFFC62828).copy(alpha = 0.85f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                            )
                                            .padding(4.dp),
                                        color = Color.White
                                    )
                                } else if (!item.isProcessed) {
                                    Text(
                                        "Processing...",
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                            )
                                            .padding(4.dp),
                                        color = Color.White
                                    )
                                }

                                // 显示图片名称
                                if (item.name.isNotEmpty()) {
                                    Text(
                                        text = item.name,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color.White,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Add Page Indicator? Optional but nice.
                    if (sessionImages.size > 1) {
                        Text(
                            "${pagerState.currentPage + 1} / ${sessionImages.size}",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White
                        )
                    }

                } else if (imageBitmap != null) {
                    // Fallback for single image non-session scenario (less likely now)
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "No captures in this session",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Full screen zoomable image dialog
            if (fullScreenImage != null) {
                Dialog(
                    onDismissRequest = { fullScreenImage = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        // Zoomable Image
                        var scale by remember { mutableStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            val maxOffsetX = (size.width * (scale - 1)) / 2
                                            val maxOffsetY = (size.height * (scale - 1)) / 2

                                            // Simple bounds check (approximate)
                                            val newX = offset.x + pan.x
                                            val newY = offset.y + pan.y

                                            offset = Offset(
                                                newX.coerceIn(-maxOffsetX, maxOffsetX),
                                                newY.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = fullScreenImage!!.bitmap,
                                contentDescription = "Full Screen Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Close button
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel), // Use system close icon or generic
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(32.dp)
                                .clickable { fullScreenImage = null }
                        )

                        // Name in full screen
                        if (fullScreenImage!!.name.isNotEmpty()) {
                            Text(
                                text = fullScreenImage!!.name,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            // 分割结果显示：处理中时在这里提示，完成后显示百分比
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Model: $modelLoadState", color = MaterialTheme.colorScheme.onSurface)
                    when {
                        uiState is PictureViewModel.PictureUiState.ReadyToAnalyze -> {
                            Text(
                                text = "Image ready. Tap Analyze to continue.",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (!isModelReady) {
                                Text(
                                    text = "Loading XNNPACK INT8 models…",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        uiState is PictureViewModel.PictureUiState.Loading -> {
                            val progress = processingProgress
                            Text(
                                text = progress?.step ?: "Processing image…",
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (!progress?.detail.isNullOrEmpty()) {
                                Text(
                                    text = progress!!.detail,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (progress != null && progress.elapsedMs > 0) {
                                Text(
                                    text = "Elapsed: ${progress.elapsedMs / 1000}.${(progress.elapsedMs % 1000) / 100}s",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            progress?.stageTimings?.forEach { timing ->
                                Text(
                                    text = "  · ${timing.stage}: ${timing.durationMs}ms",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                        uiState is PictureViewModel.PictureUiState.Error -> {
                            Text(
                                text = (uiState as PictureViewModel.PictureUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        segResult != null -> {
                            val percentText = String.format(Locale.US, "Affected area: %.2f%%", (segResult!!.percent * 100f))
                            Text(percentText, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            val modelLabel = currentImage?.modelName ?: "XNNPACK INT8"
                            if (!modelLabel.isNullOrBlank()) {
                                Text(
                                    text = "Model: $modelLabel",
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            processingProgress?.stageTimings?.forEach { timing ->
                                Text(
                                    text = "  · ${timing.stage}: ${timing.durationMs}ms",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                        else -> {
                            Text("Waiting for an image", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }


            Button(
                onClick = { viewModel.startAnalysis() },
                enabled = canStartAnalysis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (isAnalyzing) "Analyzing…" else "Analyze")
            }

            if (SHOW_MANUAL_SAVE_BUTTON) {
                // 保存结果按钮（需已有分割结果）；自动保存模式下隐藏，逻辑保留
                Button(
                    onClick = {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        dateInput = sdf.format(Date())
                        if (defaultName.isNotEmpty()) {
                            nameInput = defaultName
                        }
                        showSaveDialog = true
                    },
                    enabled = segResult != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Save Result")
                }
            }

            if (voiceStatus.isNotBlank()) {
                Text(
                    text = voiceStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }

            // 拍照按钮
            Button(
                onClick = { viewModel.takePicture() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                enabled = !taking
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_camera),
                    contentDescription = null
                )
                Spacer(Modifier.size(8.dp))
                Text("Take Photo")
            }

            OutlinedButton(
                onClick = onImportLocalImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Import Image")
            }

            // 保存对话框
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val r = segResult
                            if (r == null) {
                                showSaveDialog = false
                                return@TextButton
                            }
                            val name = nameInput.trim()
                            val date = dateInput.trim()
                            if (name.isEmpty() || date.isEmpty()) {
                                // 简单校验，真实项目可用 Snackbar/Toast
                                showSaveDialog = false
                                return@TextButton
                            }
                            viewModel.saveResult(r, name, date)
                            showSaveDialog = false
                            // nameInput = "" // 保留名字，方便连续保存同一系列
//                            dateInput = ""
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                    },
                    title = { Text("Save Analysis Result") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Name / ID") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = dateInput,
                                onValueChange = { dateInput = it },
                                label = { Text("Date") },
                                singleLine = true
                            )
                            val percentText = if (segResult != null) {
                                String.format(Locale.US, "Affected area: %.2f%%", segResult!!.percent * 100f)
                            } else {
                                ""
                            }
                            Text(percentText)
                        }
                    }
                )
            }

            // 查看历史按钮
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, PictureListActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("View History")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 测试自定义协议按钮
//            Button(
//                onClick = {
//                    val intent = Intent(context, CustomProtocolActivity::class.java)
//                    context.startActivity(intent)
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 8.dp, vertical = 4.dp)
//            ) {
//                Text("测试自定义协议（Caps）")
//            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSettingsCard(
    modelLoadState: String,
    resolutions: Array<android.util.Size>,
    selectedResolution: android.util.Size,
    onResolutionSelected: (android.util.Size) -> Unit,
    defaultName: String,
    onDefaultNameChange: (String) -> Unit,
    isAutoSave: Boolean,
    onAutoSaveChange: (Boolean) -> Unit
) {
    var resolutionExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Capture setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = modelLoadState,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (modelLoadState.startsWith("Ready")) {
                        MaterialTheme.colorScheme.primary
                    } else if (modelLoadState.startsWith("Load failed")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

//            Text(
//                text = "Fixed XNNPACK INT8 pipeline",
//                style = MaterialTheme.typography.labelLarge,
//                color = MaterialTheme.colorScheme.primary
//            )
//            FixedModelRow("Stage 1 · Coarse segmentation", "coarse_seg2class_xnnpack_int8")
//            FixedModelRow("Stage 2 · Fine segmentation", "fine_seg3class_xnnpack_int8")
            Text(
                text = "Capture resolution",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            ExposedDropdownMenuBox(
                expanded = resolutionExpanded,
                onExpandedChange = { resolutionExpanded = it }
            ) {
                TextField(
                    readOnly = true,
                    value = "${selectedResolution.width} × ${selectedResolution.height}",
                    onValueChange = {},
                    label = { Text("Glasses camera resolution") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = resolutionExpanded,
                    onDismissRequest = { resolutionExpanded = false },
                    modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
                ) {
                    resolutions.forEach { resolution ->
                        DropdownMenuItem(
                            text = { Text("${resolution.width} × ${resolution.height}") },
                            onClick = {
                                onResolutionSelected(resolution)
                                resolutionExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = defaultName,
                onValueChange = onDefaultNameChange,
                label = { Text("Series name") },
                placeholder = { Text("Leave blank to use a timestamp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save results automatically",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Store completed analyses in history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutoSave,
                    onCheckedChange = onAutoSaveChange
                )
            }
        }
    }
}

@Composable
private fun FixedModelRow(label: String, model: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = model,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        val viewModel = viewModel<PictureViewModel>()
        PictureScreen(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            onImportLocalImage = {}
        )
    }
}
