package com.rokid.cxrmsamples.activities.picture

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.bin.david.form.core.SmartTable
import com.bin.david.form.core.TableConfig
import com.bin.david.form.data.CellInfo
import com.bin.david.form.data.column.Column
import com.bin.david.form.data.format.draw.IDrawFormat
import com.bin.david.form.data.style.FontStyle
import com.bin.david.form.data.table.TableData
import com.bin.david.form.utils.DensityUtils
import com.rokid.cxrmsamples.data.Pic
import com.rokid.cxrmsamples.data.PicDBHelper
import com.rokid.cxrmsamples.data.toEnglishTimingInfo
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                ExportScreen()
            }
        }
    }
}

@Composable
private fun ExportScreen() {
    val ctx = LocalContext.current
    val db = PicDBHelper.getInstance(ctx)
    val scope = rememberCoroutineScope()
    // 优化：改为异步加载，避免数据量大时阻塞主线程导致卡顿
    val dataList = remember { mutableStateOf<List<Pic>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = db.queryAll()
            // 模拟一点延迟或者直接赋值
            dataList.value = list
            isLoading.value = false
        }
    }

    // 选中的项（用于导出图片）
    val selectedItems = remember { mutableStateListOf<Pic>() }
    // 导出文件路径
    val exportedCsvPath = remember { mutableStateOf<String?>(null) }
    val exportedZipPath = remember { mutableStateOf<String?>(null) }
    val exportStatus = remember { mutableStateOf<String?>(null) }

    // 全选/反选
    val isAllSelected = remember(dataList.value, selectedItems) {
        dataList.value.isNotEmpty() && selectedItems.size == dataList.value.size
    }

    // 主题色
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val checkMarkColor = MaterialTheme.colorScheme.onPrimary.toArgb()

    // 复用列创建逻辑
    fun createSelectColumn(context: android.content.Context, selectionColor: Int, uncheckColor: Int, checkColor: Int): Column<Boolean> {
        val col = Column<Boolean>("Selected", "isSelected")
        val size = DensityUtils.dp2px(context, 20f)

        col.drawFormat = object : IDrawFormat<Boolean> {
             val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = DensityUtils.dp2px(context, 2f).toFloat()
             }
             val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
             }
             val path = Path()

             // Allow the table to measure the cell size
             override fun measureWidth(column: Column<Boolean>?, position: Int, config: TableConfig?): Int {
                 return DensityUtils.dp2px(context, 50f)
             }

             override fun measureHeight(column: Column<Boolean>?, position: Int, config: TableConfig?): Int {
                 return DensityUtils.dp2px(context, 40f)
             }

             override fun draw(c: Canvas, rect: Rect, cellInfo: CellInfo<Boolean>, config: TableConfig) {
                 val isChecked = cellInfo.data ?: false

                 val cx = rect.centerX().toFloat()
                 val cy = rect.centerY().toFloat()
                 val radius = DensityUtils.dp2px(context, 2f).toFloat() // Rounded corners
                 val halfSize = size / 2f
                 val left = cx - halfSize
                 val top = cy - halfSize
                 val right = cx + halfSize
                 val bottom = cy + halfSize

                 if (isChecked) {
                     // Draw filled box
                     fillPaint.color = selectionColor
                     c.drawRoundRect(left, top, right, bottom, radius, radius, fillPaint)

                     // Draw checkmark
                     paint.color = checkColor
                     paint.style = Paint.Style.STROKE
                     paint.strokeCap = Paint.Cap.ROUND
                     paint.strokeJoin = Paint.Join.ROUND

                     path.reset()
                     // Checkmark points relative to box
                     // Start (left-mid)
                     path.moveTo(left + size * 0.2f, top + size * 0.5f)
                     // Mid (bottom-mid)
                     path.lineTo(left + size * 0.45f, top + size * 0.75f)
                     // End (top-right)
                     path.lineTo(left + size * 0.8f, top + size * 0.3f)

                     c.drawPath(path, paint)
                 } else {
                     // Draw border
                     paint.color = uncheckColor
                     paint.style = Paint.Style.STROKE
                     // Reset stroke style just in case
                     paint.strokeCap = Paint.Cap.BUTT
                     paint.strokeJoin = Paint.Join.MITER

                     // Inset slightly so stroke remains inside bounds or centered on bounds
                     c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
                 }
             }
        }

        col.setOnColumnItemClickListener { _, _, _, position ->
            if (position >= 0 && position < dataList.value.size) {
                val item = dataList.value[position]
                if (selectedItems.contains(item)) {
                    selectedItems.remove(item)
                } else {
                    selectedItems.add(item)
                }
            }
        }
        return col
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Export Center",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Total records: ${dataList.value.size}")
                Text("Selected: ${selectedItems.size}")
                Text("CSV includes affected area, timing, model, and image fields")
                exportStatus.value?.let { status ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Status: $status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        /** 顶部操作区 */
        // 第一行：CSV 相关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                onClick = {
                    scope.launch {
                        exportStatus.value = "Exporting CSV..."
                        val result = withContext(Dispatchers.IO) {
                            val baseDir = ctx.getExternalFilesDir(null) ?: return@withContext null
                            val outDir = File(baseDir, "bhne")
                            if (!outDir.exists()) outDir.mkdirs()

                            val ts = SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.US
                            ).format(Date())
                            val file = File(outDir, "export_$ts.csv")
                            writeCsv(file, dataList.value)
                            file
                        }

                        if (result == null) {
                            exportStatus.value = "Export failed: external storage is unavailable"
                            Toast.makeText(ctx, "External storage is unavailable", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        exportedCsvPath.value = result.absolutePath
                        exportStatus.value = "CSV export complete: ${result.name}"
                        Toast.makeText(
                            ctx,
                            "CSV exported to ${result.parentFile?.absolutePath}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                Text("Export All to CSV")
            }

            Button(
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                onClick = {
                val path = exportedCsvPath.value
                if (path.isNullOrEmpty()) {
                    Toast.makeText(ctx, "Export a CSV file first", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                shareFile(ctx, File(path))
            }) {
                Text("Share CSV")
            }
        }

        // 第二行：图片导出相关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
             Button(
                 modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                 enabled = selectedItems.isNotEmpty(),
                 onClick = {
                     scope.launch {
                         exportStatus.value = "Packaging images..."
                         val selectedSnapshot = selectedItems.toList()
                         val result = withContext(Dispatchers.IO) {
                             try {
                                 val baseDir = ctx.getExternalFilesDir(null) ?: return@withContext null
                                 val outDir = File(baseDir, "bhne")
                                 if (!outDir.exists()) outDir.mkdirs()

                                 val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                 val zipFile = File(outDir, "images_$ts.zip")
                                 zipImages(selectedSnapshot, zipFile)
                                 zipFile
                             } catch (_: Exception) {
                                 null
                             }
                         }

                         if (result == null) {
                             exportStatus.value = "Image package failed"
                             Toast.makeText(ctx, "Unable to package images", Toast.LENGTH_SHORT).show()
                             return@launch
                         }

                         exportedZipPath.value = result.absolutePath
                         exportStatus.value = "Image package complete: ${result.name}"
                         Toast.makeText(ctx, "Image package ready: ${result.name}", Toast.LENGTH_SHORT).show()
                     }
                 }
             ) {
                 Text("Export Selected Images (${selectedItems.size})")
             }

             Button(
                 modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                 onClick = {
                     val path = exportedZipPath.value
                     if (path.isNullOrEmpty()) {
                         Toast.makeText(ctx, "Export selected images first", Toast.LENGTH_SHORT).show()
                         return@Button
                     }
                     shareFile(ctx, File(path))
                 }
             ) {
                 Text("Share Image Package")
             }
        }

        // 全选控制
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isAllSelected) {
                        selectedItems.clear()
                    } else {
                        selectedItems.clear()
                        selectedItems.addAll(dataList.value)
                    }
                }
        ) {
            Checkbox(
                checked = isAllSelected,
                onCheckedChange = { checked ->
                    if (checked) {
                        selectedItems.clear()
                        selectedItems.addAll(dataList.value)
                    } else {
                        selectedItems.clear()
                    }
                }
            )
            Text("Select All", modifier = Modifier.padding(start = 8.dp))
        }

        /** 标题区 */
        Text(
            text = "Data Preview (${dataList.value.size} records)",
            style = MaterialTheme.typography.titleMedium
        )

        /** 表格区 */
        // 让表格在剩余空间内尽量占满一行并可滚动
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
            if (isLoading.value) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SmartTable<ExportRow>(context).apply {

                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            /** 全局字体（表头 + 内容），略大提升可读性 */
                            FontStyle.setDefaultTextSize(
                                DensityUtils.sp2px(context, 18f)
                            )

                            /** 基础表格配置 */
                            config.isShowTableTitle = false
                            config.isShowXSequence = false
                            config.isShowYSequence = false
                            setZoom(true)

                            // 行高、列最小宽度，避免过拥挤
                            config.minTableWidth = DensityUtils.dp2px(context, 0f)
                            config.horizontalPadding = DensityUtils.dp2px(context, 8f)
                            config.verticalPadding = DensityUtils.dp2px(context, 10f)

                            /** 列定义 */
                            val colSelect = createSelectColumn(context, primaryColor, uncheckedColor, checkMarkColor)

                            val colName = Column<String>("Sample Name", "name")
                            val colSeverity = Column<String>("Affected Area (%)", "severity").apply {
                                setTextAlign(Paint.Align.CENTER)
                            }
                            val colDate = Column<String>("Captured At", "date")

                            /** 数据映射 */
                            val rows = dataList.value.map {
                                ExportRow(
                                    isSelected = false,
                                    name = it.name,
                                    severity = String.format(Locale.US, "%.2f", it.percent * 100),
                                    date = formatDate(it.date)
                                )
                            }

                            setTableData(
                                TableData(
                                    "Analysis Data",
                                    rows,
                                    colSelect,
                                    colName,
                                    colSeverity,
                                    colDate
                                )
                            )
                        }
                    },
                    update = { table ->
                        // 数据更新时刷新
                        val rows = dataList.value.map { pic ->
                            ExportRow(
                                isSelected = selectedItems.contains(pic),
                                name = pic.name,
                                severity = String.format(Locale.US, "%.2f", pic.percent * 100),
                                date = formatDate(pic.date)
                            )
                        }

                        // 重新定义列以包含"选中"状态
                        val colSelect = createSelectColumn(table.context, primaryColor, uncheckedColor, checkMarkColor)

                        val colName = Column<String>("Sample Name", "name")
                        val colSeverity = Column<String>("Affected Area (%)", "severity").apply {
                            setTextAlign(Paint.Align.CENTER)
                        }
                        val colDate = Column<String>("Captured At", "date")

                        val tableData = TableData(
                            "Analysis Data",
                            rows,
                            colSelect,
                            colName,
                            colSeverity,
                            colDate
                        )
                        tableData.isShowCount = false
                        table.tableData = tableData
                    }
                )
            }
        }

    }
}

/** 表格展示模型 */
data class ExportRow(
    val isSelected: Boolean,
    val name: String,
    val severity: String,
    val date: String
)

private fun shareFile(ctx: android.content.Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(ctx, "File not found", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        ctx,
        "com.rokid.cxrmsamples.fileprovider",
        file
    )
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            // 使用通用类型
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Unable to open the share sheet: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun zipImages(pics: List<Pic>, zipFile: File) {
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        pics.forEach { pic ->
            val srcFile = File(pic.pathSeg)
            if (srcFile.exists()) {
                val entryName = buildImageEntryName(pic)
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(srcFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}

/** CSV 导出 */
private fun writeCsv(file: File, list: List<Pic>) {
    FileOutputStream(file).use { fos ->
        // UTF-8 BOM，避免 Windows Excel 打开中文乱码
        fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        fos.write(
            "Sample Name,Affected Area (%),Raw Probability (0-1),Captured At,Created Timestamp,Inference Timing,Model,Image File Name,Analyzed Image Path,Original Image Path\n"
                .toByteArray()
        )
        list.forEach {
            val imageName = buildImageEntryName(it)
            val line =
                    "${csvEscape(it.name)}," +
                    "${String.format(Locale.US, "%.2f", it.percent * 100)}," +
                    "${String.format(Locale.US, "%.4f", it.percent)}," +
                    "${csvEscape(it.date)}," +
                    "${it.createdAt}," +
                    "${csvEscape(it.timingInfo.orEmpty().toEnglishTimingInfo())}," +
                    "${csvEscape(it.modelName.orEmpty())}," +
                    "${csvEscape(imageName)}," +
                    "${csvEscape(it.pathSeg)}," +
                    "${csvEscape(it.pathFull.orEmpty())}\n"
            fos.write(line.toByteArray())
        }
    }
}

private fun buildImageEntryName(pic: Pic): String {
    val sourceName = File(pic.pathSeg).name
    return if (sourceName.isNotBlank()) sourceName else "${pic.name}.jpg"
}

private fun csvEscape(input: String): String {
    if (input.isEmpty()) return ""
    val escaped = input.replace("\"", "\"\"")
    return "\"$escaped\""
}

/** 时间统一格式 */
private fun formatDate(src: String): String {
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val outFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        outFmt.format(inFmt.parse(src)!!)
    } catch (_: Exception) {
        src
    }
}
