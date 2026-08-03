package com.rokid.cxrmsamples.activities.picture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import java.io.File
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.tooling.preview.Preview
import java.util.Locale
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme
import com.rokid.cxrmsamples.data.toEnglishTimingInfo

class PictureDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pathSeg = intent.getStringExtra(EXTRA_PATH_SEG)
        val percent = intent.getFloatExtra(EXTRA_PERCENT, 0f)
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val date = intent.getStringExtra(EXTRA_DATE) ?: ""
        val createdAt = intent.getLongExtra(EXTRA_CREATED_AT, 0L)
        val timingInfo = intent.getStringExtra(EXTRA_TIMING_INFO)
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
        val diseaseLevel = intent.getStringExtra(EXTRA_DISEASE_LEVEL)
        setContent {
            CXRMSamplesTheme {
                PictureDetailScreen(pathSeg, percent, name, date, createdAt, timingInfo, modelName, diseaseLevel) {
                    finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_PATH_SEG = "path_seg"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_NAME = "name"
        const val EXTRA_DATE = "date"
        const val EXTRA_CREATED_AT = "created_at"
        const val EXTRA_TIMING_INFO = "timing_info"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_DISEASE_LEVEL = "disease_level"
    }
}

@Composable
fun PictureDetailScreen(
    pathSeg: String?,
    percent: Float,
    name: String,
    date: String,
    createdAt: Long,
    timingInfo: String?,
    modelName: String?,
    diseaseLevel: String?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Analysis Detail", style = MaterialTheme.typography.headlineMedium)
        val bmp = if (pathSeg != null && File(pathSeg).exists()) BitmapFactory.decodeFile(pathSeg) else null
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Analyzed image",
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("Image not found", color = MaterialTheme.colorScheme.error)
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine("Name", name)
                DetailLine("Date", date)
                DetailLine("Affected area", String.format(Locale.US, "%.2f%%", percent * 100f))
                if (!diseaseLevel.isNullOrBlank()) DetailLine("Disease level", diseaseLevel)
                if (!modelName.isNullOrBlank()) DetailLine("Model", modelName)
                if (createdAt > 0) DetailLine("Saved timestamp", createdAt.toString())
                if (!timingInfo.isNullOrBlank()) DetailLine("Stage timing", timingInfo.toEnglishTimingInfo())
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            val ctx = LocalContext.current
            Button(onClick = {
                val intent = Intent(ctx, PictureActivity::class.java)
                ctx.startActivity(intent)
            }, modifier = Modifier.weight(1f)) {
                Text("Capture")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPictureDetailScreen() {
    PictureDetailScreen(
        pathSeg = null,
        percent = 0.85f,
        name = "Sample image",
        date = "2023-10-10",
        createdAt = System.currentTimeMillis(),
        timingInfo = "Decode=50ms; Stage 1=1200ms; Stage 2=28000ms",
        modelName = "MedViT INT8",
        diseaseLevel = "level3",
        onBack = {}
    )
}
