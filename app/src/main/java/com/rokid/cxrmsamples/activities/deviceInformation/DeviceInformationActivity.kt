package com.rokid.cxrmsamples.activities.deviceInformation

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

class DeviceInformationActivity : ComponentActivity() {

    private val viewModel: DeviceInformationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                DeviceInformationScreen(
                    viewModel, getAllInformation = {
                        viewModel.getDeviceInformation()
                    }, listenVolume = {
                        viewModel.toSetSoundVolumeListener()
                    }, listenBrightness = {
                        viewModel.toSetBrightnessListener()
                    }, listenBattery = {
                        viewModel.toSetBatteryListener()
                    },
                    listenScreen = {
                        viewModel.toSetScreenListener()
                    }, screenOff = {
                        viewModel.notifyScreenOff()
                    }, volumeUp = {
                        viewModel.volumeUp()
                    }, volumeDown = {
                        viewModel.volumeDown()
                    }, brightnessUp = {
                        viewModel.brightnessUp()
                    }, brightnessDown = {
                        viewModel.brightnessDown()
                    })
            }
        }
    }
}

@Composable
fun DeviceInformationScreen(
    viewModel: DeviceInformationViewModel,
    getAllInformation: () -> Unit,
    listenVolume: () -> Unit,
    listenBrightness: () -> Unit,
    listenBattery: () -> Unit,
    listenScreen: () -> Unit,
    screenOff: () -> Unit,
    volumeUp: () -> Unit,
    volumeDown:()-> Unit,
    brightnessUp:()-> Unit,
    brightnessDown:()-> Unit
) {
    val deviceName = viewModel.deviceName.collectAsState()
    val deviceId = viewModel.deviceId.collectAsState()
    val systemVersion = viewModel.systemVersion.collectAsState()
    val wearingState = viewModel.wearingState.collectAsState()
    val batteryLevel = viewModel.batteryLevel.collectAsState()
    val isCharging = viewModel.isCharging.collectAsState()
    val brightness = viewModel.brightness.collectAsState()
    val soundVolume = viewModel.soundVolume.collectAsState()
    val isScreenOn = viewModel.isScreenOn.collectAsState()

    val isSetScreenOn = viewModel.screenListenerSet.collectAsState()
    val isSetBrightness = viewModel.brightnessListenerSet.collectAsState()
    val isSetVolume = viewModel.volumeListenerSet.collectAsState()
    val isSetBattery = viewModel.batteryListenerSet.collectAsState()

    val setVolume = viewModel.setVolume.collectAsState()
    val setBrightness = viewModel.setBrightness.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            contentDescription = null,
            alpha = 0.3f,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "", modifier = Modifier.height(32.dp))
            if (!deviceName.value.isNullOrEmpty()) {
                Text(text = "Device Name: ${deviceName.value ?: ""}", fontSize = 18.sp)
            }
            if (!deviceId.value.isNullOrEmpty()) {
                Text(text = "SN: ${deviceId.value ?: ""}", fontSize = 14.sp)
            }
            if (!systemVersion.value.isNullOrEmpty()) {
                Text(text = "System Version: ${systemVersion.value ?: ""}", fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (!wearingState.value.isNullOrEmpty()) {
                    Text(
                        text = "Wearing State: ${if (wearingState.value == "1") "Wearing" else "Not Wearing"}",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isSetScreenOn.value && isScreenOn.value != null) {
                    Text(
                        text = "Glasses Screen State: ${if (isScreenOn.value == true) "On" else "Off"}",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (batteryLevel.value != -1) {
                    Text(
                        text = "Battery Level: ${batteryLevel.value}%",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isCharging.value != null) {
                    Text(
                        text = "Is Charging: ${if (isCharging.value == true) "Yes" else "No"}",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (soundVolume.value != -1) {
                    Text(
                        text = "Volume Level: ${soundVolume.value}%",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (brightness.value != -1) {
                    Text(
                        text = "Brightness: ${brightness.value}",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = getAllInformation) {
                Text(text = "Get Device All Information")
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = listenVolume, modifier = Modifier.weight(1f)) {
                    Text(text = if (isSetVolume.value) "Remove V Listener" else "Listen Volume")
                }
                Button(onClick = listenBrightness, modifier = Modifier.weight(1f)) {
                    Text(text = if (isSetBrightness.value) "Remove B Listener" else "Listen Brightness")
                }
            }

            Button(onClick = listenBattery, modifier = Modifier.fillMaxWidth(0.85f)) {
                Text(text = if (isSetBattery.value) "Remove Battery Listener" else "Listen Battery")
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = listenScreen, modifier = Modifier.weight(1f)) {
                    Text(text = if (isSetScreenOn.value) "Remove S Listener" else "Listen Screen")
                }
                Button(onClick = screenOff, modifier = Modifier.weight(1f)) {
                    Text(text = "Screen Off")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Sound Volume: ", fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                Row(
                    modifier = Modifier.weight(0.6f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = volumeUp, shape = RoundedCornerShape(0.0f), enabled = setVolume.value != 100) {
                        Text(text = "+")
                    }
                    Text(
                        text = "${setVolume.value}",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Button(onClick = volumeDown, shape = RoundedCornerShape(0.0f), enabled = setVolume.value != 0) {
                        Text(text = "-")
                    }
                }

            }

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brightness: ",
                    fontSize = 14.sp,
                    modifier = Modifier.weight(0.4f),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.weight(0.6f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = brightnessUp, shape = RoundedCornerShape(0.0f), enabled = setBrightness.value != 15) {
                        Text(text = "+")
                    }
                    Text(
                        text = "${setBrightness.value}",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Button(onClick = brightnessDown, shape = RoundedCornerShape(0.0f), enabled = setBrightness.value != 0) {
                        Text(text = "-")
                    }
                }

            }

        }

    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        DeviceInformationScreen(
            viewModel = viewModel { DeviceInformationViewModel() },
            getAllInformation = {},
            listenVolume = {},
            listenBattery = {},
            listenBrightness = {},
            listenScreen = {},
            screenOff = {},
            volumeUp = {},
            volumeDown = {},
            brightnessUp = {},
            brightnessDown = {}
        )
    }
}