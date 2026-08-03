package com.rokid.cxrmsamples.activities.main

import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.activities.picture.PictureListActivity
import com.rokid.cxrmsamples.activities.picture.PictureActivity
import com.rokid.cxrmsamples.comm.GlobalCustomCmdHandler

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    private lateinit var bluetoothManager: BluetoothManager

    private val openBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {// 打开了蓝牙
            viewModel.checkBluetoothEnabled(bluetoothManager.adapter)
        }
    }

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkPermission(this, bluetoothManager)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val intent = Intent(this@MainActivity, PictureActivity::class.java).apply {
                putExtra(PictureActivity.EXTRA_IMPORT_LOCAL_IMAGE_URI, uri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", "onCreate called")
        try {
            viewModel = viewModels<MainViewModel>().value
            Log.i("MainActivity", "ViewModel initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing ViewModel", e)
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: Could not initialize the application.")
                }
            }
            return
        }

        enableEdgeToEdge()
        // Register global CUSTOM_CMD listener so app can receive commands like take_picture
        GlobalCustomCmdHandler.register()
        setContent {
            CXRMSamplesTheme {
                val state = viewModel.bluetoothState.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreenState(
                        state = state,
                        onButtonClick = {
                            when (viewModel.bluetoothState.value) {
                                BluetoothState.PERMISSION_REQUIRED -> {
                                    Log.e("MainActivity", "permission required")
                                    viewModel.requestBluetoothPermission(requestBluetoothPermission)
                                }
                                BluetoothState.BLUETOOTH_DISABLED -> {
                                    viewModel.requestBluetoothEnable(openBluetoothLauncher)
                                }
                                BluetoothState.BLUETOOTH_READY -> {
                                    viewModel.toInit(this@MainActivity)
                                }
                            }
                        },
                        onHistoryClick = {
                            startActivity(Intent(this@MainActivity, PictureListActivity::class.java))
                        },
                        onImportLocalImageClick = {
                            pickImageLauncher.launch("image/*")
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        viewModel.checkPermission(this, bluetoothManager)
    }

    override fun onResume() {
        super.onResume()
        // 强制重新注册以防止被其他页面覆盖
        try {
            GlobalCustomCmdHandler.register()
        } catch (t: Throwable) {
            Log.w("MainActivity", "onResume register failed: ${t.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        // Do NOT unregister in onStop or onDestroy, as we want to listen in background or across lifecycle
        // GlobalCustomCmdHandler.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister global listener to avoid leaks - BUT wait, if we want global listening, maybe we shouldn't?
        // If this activity is destroyed but app process lives, we lose the listener.
        // Let's remove unregister to keep it alive as long as the process is alive.
        // GlobalCustomCmdHandler.unregister()
        Log.i("MainActivity", "onDestroy called - NOT unregistering GlobalCustomCmdHandler to keep listening active")
    }
}

@Composable
fun MainScreenState(
    state: State<BluetoothState>,
    onButtonClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onImportLocalImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 控制弹窗是否展示
    var showDialog by remember { mutableStateOf(false) }
    // 记录已处理过的异常状态，避免反复弹窗
    var lastHandledState by remember { mutableStateOf<BluetoothState?>(null) }

    // 监听蓝牙状态变化：仅在进入异常态时弹一次
    LaunchedEffect(state.value) {
        val current = state.value
        if ((current == BluetoothState.PERMISSION_REQUIRED || current == BluetoothState.BLUETOOTH_DISABLED) && current != lastHandledState) {
            showDialog = true
            lastHandledState = current
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            contentDescription = null,
            alpha = 0.12f,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 不再展示任何状态文案，仅展示主业务 UI
            Text(
                text = stringResource(R.string.hello_rokid),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when (state.value) {
                    BluetoothState.PERMISSION_REQUIRED -> "Permission required"
                    BluetoothState.BLUETOOTH_DISABLED -> "Bluetooth unavailable"
                    BluetoothState.BLUETOOTH_READY -> "Glasses connection ready"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = onButtonClick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(text = when (state.value) {
                    BluetoothState.PERMISSION_REQUIRED -> stringResource(R.string.button_request_permission)
                    BluetoothState.BLUETOOTH_DISABLED -> stringResource(R.string.button_open_bluetooth)
                    BluetoothState.BLUETOOTH_READY -> stringResource(R.string.button_to_init)
                })
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(
                onClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(text = stringResource(id = R.string.button_view_history))
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(
                onClick = onImportLocalImageClick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(text = "Import Local Image")
            }
        }

        // 一次性弹窗提示
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = when (state.value) {
                            BluetoothState.PERMISSION_REQUIRED -> "Bluetooth Permission Required"
                            BluetoothState.BLUETOOTH_DISABLED -> "Bluetooth Is Off"
                            else -> ""
                        }
                    )
                },
                text = {
                    Text(
                        text = when (state.value) {
                            BluetoothState.PERMISSION_REQUIRED -> "Allow Bluetooth access to connect to your Rokid glasses."
                            BluetoothState.BLUETOOTH_DISABLED -> "Turn on Bluetooth to connect to your Rokid glasses."
                            else -> ""
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        // 可选：跳转系统设置 / 请求权限，由外部 onButtonClick 处理
                    }) {
                        Text("Got It")
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        val fakeState = remember { mutableStateOf(BluetoothState.BLUETOOTH_READY) }
        MainScreenState(state = fakeState, onButtonClick = {}, onHistoryClick = {}, onImportLocalImageClick = {})
    }
}
