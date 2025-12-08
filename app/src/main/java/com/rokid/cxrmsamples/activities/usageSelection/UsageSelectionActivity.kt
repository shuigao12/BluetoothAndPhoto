package com.rokid.cxrmsamples.activities.usageSelection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.dataBeans.UsageType
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

class UsageSelectionActivity : ComponentActivity() {

    private val viewModel: UsageSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                UsageSelectionScreen(onClick = {type->
                    viewModel.toUsage(this, type)
                })
            }
        }
    }
}

@Composable
fun UsageSelectionScreen(onClick: (UsageType) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.3f
        )
        Column(modifier = Modifier.fillMaxWidth(0.8f),

            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                onClick(UsageType.USAGE_TYPE_DEVICE_INFORMATION)
            }) {
                Text(text = "获取设备信息")
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                onClick(UsageType.USAGE_TYPE_PHOTO)
            }) {
                Text(text = "拍照")
            }


            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                onClick(UsageType.USAGE_CUSTOM_VIEW)
            }) {
                Text(text = "自定义View")
            }

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                onClick(UsageType.USAGE_TYPE_CUSTOM_PROTOCOL)
            }) {
                Text(text = "自定义协议")
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun UsageSelectionScreenPreview() {
    CXRMSamplesTheme {
        UsageSelectionScreen(onClick = {})
    }
}