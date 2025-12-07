package com.rokid.cxrmsamples.activities.main

import android.bluetooth.BluetoothManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

import com.rokid.cxrmsamples.R
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private lateinit var bluetoothManager: BluetoothManager

    private val openBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {result ->
        if (result.resultCode == RESULT_OK){// 打开了蓝牙
            viewModel.checkBluetoothEnabled(bluetoothManager.adapter)
        }
    }

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.checkPermission(this, bluetoothManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                MainScreen(viewModel, onButtonClick = {
                    when (viewModel.bluetoothState.value){
                        BluetoothState.PERMISSION_REQUIRED -> {
                            Log.e("MainActivity", "permission required")
                            viewModel.requestBluetoothPermission(requestBluetoothPermission)
                        }
                        BluetoothState.BLUETOOTH_DISABLED -> {
                            viewModel.requestBluetoothEnable(openBluetoothLauncher)
                        }
                        BluetoothState.BLUETOOTH_READY -> {
                            viewModel.toInit(this)
                        }
                    }
                })
            }
        }
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        viewModel.checkPermission(this, bluetoothManager)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), onButtonClick:()->Unit) {
    val state = viewModel.bluetoothState.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Image(// background
            painter = painterResource(id = R.drawable.glasses_bg),
            contentDescription = null,
            alpha = 0.3f,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(// app name
                text = when (state.value) {
                    BluetoothState.PERMISSION_REQUIRED -> stringResource(R.string.bluetooth_permission_request)
                    BluetoothState.BLUETOOTH_DISABLED -> stringResource(R.string.bluetooth_closed)
                    BluetoothState.BLUETOOTH_READY -> stringResource(R.string.ready)
                },
                textAlign = TextAlign.Start,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(// welcome
                text = stringResource(R.string.hello_rokid),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onButtonClick) {// button
                Text(text = when(state.value){
                    BluetoothState.PERMISSION_REQUIRED -> stringResource(R.string.button_request_permission)
                    BluetoothState.BLUETOOTH_DISABLED -> stringResource(R.string.button_open_bluetooth)
                    BluetoothState.BLUETOOTH_READY -> stringResource(R.string.button_to_init)
                })
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        MainScreen(viewModel { MainViewModel() }, onButtonClick = {})
    }
}