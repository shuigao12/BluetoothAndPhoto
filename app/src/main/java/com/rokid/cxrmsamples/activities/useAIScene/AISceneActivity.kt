package com.rokid.cxrmsamples.activities.useAIScene

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

class AISceneActivity : ComponentActivity() {

    private val viewModel: AISceneViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                AISceneScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun AISceneScreen(viewModel: AISceneViewModel) {
    val aiStatus by viewModel.aiSceneUiState.collectAsState()
    val isDeviceControl by viewModel.isDeviceControl.collectAsState()
    val hasPhoto by viewModel.hasPhotoRequest.collectAsState()
    val photoGet by viewModel.photoGet.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            modifier = Modifier.fillMaxSize(),
            contentDescription = null,
            alpha = 0.3f
        )
        Text(
            text = "欢迎使用AI场景",
            modifier = Modifier
                .padding(top = 32.dp)
                .align(Alignment.TopCenter)
        )
        Column(
            modifier = Modifier
                .padding(top = 64.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!aiStatus) {
                Text(text = "请长按TouchPad或使用语音 \"乐奇\" 唤醒AI场景")
            } else {
                Button(onClick = { viewModel.whenASREnd() }) {
                    Text(text = "模拟ASR 结束")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 意图指向设备控制 CheckBox
                    Checkbox(
                        checked = isDeviceControl,
                        onCheckedChange = { checked ->
                            viewModel.setDeviceControl(checked)
                        }
                    )
                    Text(text = "意图指向设备控制")
                    // 只有当"意图指向设备控制"未被选中时，才显示"是否需要图片" CheckBox
                    if (!isDeviceControl) {
                        Checkbox(
                            checked = hasPhoto,
                            onCheckedChange = { checked ->
                                viewModel.setHasPhotoRequest(checked)
                            }
                        )
                        Text(text = "是否识别照片")
                    }
                }
                if (photoGet != null && hasPhoto) {
                    Image(
                        modifier = Modifier.padding(top = 32.dp),
                        bitmap = photoGet!!,
                        contentDescription = null
                    )
                }


            }
        }


    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        AISceneScreen(viewModel = viewModel { AISceneViewModel() })
    }
}