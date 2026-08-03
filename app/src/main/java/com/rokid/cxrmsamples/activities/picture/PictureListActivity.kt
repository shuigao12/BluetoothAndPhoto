package com.rokid.cxrmsamples.activities.picture

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rokid.cxrmsamples.data.Pic
import com.rokid.cxrmsamples.data.PicDBHelper
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PictureListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                PictureListScreenContainer(
                    onItemClick = { pic ->
                        startActivity(
                            Intent(this, PictureDetailActivity::class.java).apply {
                                putExtra(PictureDetailActivity.EXTRA_PATH_SEG, pic.pathSeg)
                                putExtra(PictureDetailActivity.EXTRA_PERCENT, pic.percent)
                                putExtra(PictureDetailActivity.EXTRA_NAME, pic.name)
                                putExtra(PictureDetailActivity.EXTRA_DATE, pic.date)
                                putExtra(PictureDetailActivity.EXTRA_CREATED_AT, pic.createdAt)
                                putExtra(PictureDetailActivity.EXTRA_TIMING_INFO, pic.timingInfo)
                                putExtra(PictureDetailActivity.EXTRA_MODEL_NAME, pic.modelName)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PictureListScreenContainer(onItemClick: (Pic) -> Unit) {
    val ctx = LocalContext.current
    val listState = remember { mutableStateOf<List<Pic>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val showClearAll = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            listState.value = PicDBHelper.getInstance(ctx).queryAll()
            isLoading.value = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        /** 顶部操作区：主次分明 */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Analysis History", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${listState.value.size} records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    ctx.startActivity(Intent(ctx, ExportActivity::class.java))
                }
            ) {
                Text("Export")
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = { showClearAll.value = true }) {
                Text("Clear All")
            }
        }

        /** 列表区 */
        if (isLoading.value) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (listState.value.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved analyses", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(listState.value) { pic ->
                    PictureListItem(
                        pic = pic,
                        onClick = onItemClick,
                        onDelete = { deletedPic ->
                            // 更新界面列表
                            listState.value = listState.value - deletedPic
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    /** 清空确认 */
    if (showClearAll.value) {
        AlertDialog(
            onDismissRequest = { showClearAll.value = false },
            title = { Text("Clear all records?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        PicDBHelper.getInstance(ctx).deleteAll()
                        listState.value = emptyList()
                        showClearAll.value = false
                    }
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PictureListItem(
    pic: Pic,
    onClick: (Pic) -> Unit,
    onDelete: (Pic) -> Unit = {}
) {
    val ctx = LocalContext.current
    val showEdit = remember { mutableStateOf(false) }
    val showDelete = remember { mutableStateOf(false) }
    val showImage = remember { mutableStateOf(false) }

    // 异步加载缩略图
    val bmpState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pic.pathSeg) {
        withContext(Dispatchers.IO) {
            bmpState.value = decodeThumbnail(pic.pathSeg)
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(pic) },
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            /** 信息区 */
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bmp = bmpState.value

                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable { showImage.value = true }
                    )
                } else {
                    Box(
                        modifier = Modifier.size(88.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(pic.name, style = MaterialTheme.typography.titleMedium)
                    Text(pic.date, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = String.format(
                            Locale.US,
                            "Affected area %.2f%%",
                            pic.percent * 100
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!pic.modelName.isNullOrBlank()) {
                        Text(
                            text = "Model: ${pic.modelName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            /** 操作区 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
//                TextButton(onClick = { onClick(pic) }) {
//                    Text("查看")
//                }
                TextButton(onClick = { showEdit.value = true }) {
                    Text("Edit")
                }
                TextButton(
                    onClick = { showDelete.value = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }

    /** 编辑 */
    if (showEdit.value) {
        EditDialog(
            pic = pic,
            onDismiss = { showEdit.value = false }
        ) { newName, newDate ->
            PicDBHelper.getInstance(ctx).update(pic.name, newName, newDate)
            pic.name = newName
            pic.date = newDate
            showEdit.value = false
        }
    }

    /** 删除 */
    if (showDelete.value) {
        AlertDialog(
            onDismissRequest = { showDelete.value = false },
            title = { Text("Delete this record?") },
            text = { Text("${pic.name}\n${pic.date}") },
            confirmButton = {
                Button(onClick = {
                    PicDBHelper.getInstance(ctx).delete(pic.name)
                    showDelete.value = false
                    onDelete(pic)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    /** 查看图片 */
    if (showImage.value) {
        AlertDialog(
            onDismissRequest = { showImage.value = false },
            title = { Text("Analysis Image") },
            text = {
                val file = File(pic.pathSeg)
                if (file.exists()) {
                    val fullBmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (fullBmp != null) {
                        Image(
                            bitmap = fullBmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        )
                    }
                } else {
                    Text("Image not found")
                }
            },
            confirmButton = {
                Button(onClick = { showImage.value = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun decodeThumbnail(path: String, reqSize: Int = 160): android.graphics.Bitmap? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)

        var sample = 1
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        while (maxDim / sample > reqSize * 2) sample *= 2

        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }.let {
            BitmapFactory.decodeFile(file.absolutePath, it)
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun EditDialog(
    pic: Pic,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val nameState = remember { mutableStateOf(pic.name) }
    val dateState = remember { mutableStateOf(pic.date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameState.value,
                    onValueChange = { nameState.value = it },
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = dateState.value,
                    onValueChange = { dateState.value = it },
                    label = { Text("Date") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(nameState.value, dateState.value)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
