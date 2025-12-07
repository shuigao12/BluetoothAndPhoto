package com.rokid.cxrmsamples.activities.bluetoothConnection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

class BluetoothInitActivity : ComponentActivity() {

    private val viewModel: BluetoothIniViewModel by viewModels()
    lateinit var btManager: BluetoothManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                BluetoothInitScreen(viewModel = viewModel, reconnect = {
                    viewModel.connectBTSocket(this)
                }, scan = {
                    viewModel.handleScan(btManager.adapter.bluetoothLeScanner)
                }, onItemClicked = { deviceItem ->
                    viewModel.handleScan(btManager.adapter.bluetoothLeScanner)
                    viewModel.deviceClicked(this, deviceItem)
                }, onToast = {
                    Toast.makeText(
                        this@BluetoothInitActivity,
                        resources.getString(R.string.bt_connecting),
                        Toast.LENGTH_SHORT
                    ).show()
                }, clear = {
                    viewModel.clearDevices()
                }, doAfterConnected = {
                    viewModel.record(this)
                }, disconnect = {
                    viewModel.disconnect()
                }, toUseGlasses = {
                    viewModel.toUseGlasses(this)
                })
            }
        }
        btManager = getSystemService(BluetoothManager::class.java)
        viewModel.toConnect.observe(this) {
            if (it) {
                viewModel.connectBTSocket(this)
            }
        }
        viewModel.checkRecordState(this)
        viewModel.checkConnection()
    }

}

//Jetpack Compose

@SuppressLint("MissingPermission")
@Composable
fun BluetoothInitScreen(
    viewModel: BluetoothIniViewModel = viewModel(),
    reconnect: () -> Unit,
    scan: () -> Unit,
    onItemClicked: (DeviceItem?) -> Unit,
    onToast: () -> Unit,
    clear: () -> Unit,
    doAfterConnected: () -> Unit,
    disconnect: () -> Unit,
    toUseGlasses: () -> Unit
) {
    val recordState = viewModel.recordState.collectAsState()
    val scanning = viewModel.isScanningState.collectAsState()
    val devices = viewModel.devicesList.collectAsState()

    val recordName = viewModel.recordName.collectAsState()
    val recordMacAddress = viewModel.recordMacAddress.collectAsState()
    val recordUuid = viewModel.recordUUID.collectAsState()
    val connecting = viewModel.connecting.collectAsState()
    val connected = viewModel.connected.collectAsState()
    if (connected.value) {
        doAfterConnected()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.glasses_bg),
            modifier = Modifier.fillMaxSize(),
            alpha = 0.3f,
            contentDescription = null
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "", modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
            Text(
                text = if (recordState.value) {
                    stringResource(R.string.is_record)
                } else {
                    stringResource(R.string.not_record)
                },
                modifier = Modifier
            )
            if (recordState.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)

                ) {
                    Text(
                        text = stringResource(R.string.device_record),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = recordName.value ?: "",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)

                ) {
                    Text(
                        text = stringResource(R.string.mac_address_record),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = recordMacAddress.value ?: "",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)

                ) {
                    Text(
                        text = stringResource(R.string.uuid_record),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = recordUuid.value ?: "",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
                if (!connected.value) {
                    Button(onClick = reconnect, modifier = Modifier.fillMaxWidth(0.8f)) {
                        Text(text = stringResource(R.string.reconnect))
                    }
                }

            }
            if (!connected.value) {
                Row(modifier = Modifier.fillMaxWidth(0.8f)) {
                    Button(
                        onClick = scan, modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 4.dp)
                            .offset(y = 12.dp)
                    ) {
                        Text(
                            text = if (!scanning.value) {
                                stringResource(R.string.scan)
                            } else {
                                stringResource(R.string.stop_scan)
                            }
                        )
                    }
                    if (!scanning.value && devices.value.isNotEmpty()) {
//                if (!scanning.value){
                        Button(
                            onClick = clear,
                            modifier = Modifier
                                .offset(y = 12.dp)
                                .padding(start = 4.dp, end = 4.dp)
                        ) {
                            Text(text = stringResource(R.string.clear_items))
                        }
                    }
                }
            }

            // 扫描结果列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(devices.value) { deviceItem ->
                    BluetoothDeviceItem(
                        item = deviceItem,
                        onClick = {
                            if (!connecting.value) {
                                onItemClicked(deviceItem)
                            } else {
                                onToast()
                            }
                        }
                    )
                }
            }

            if (connected.value) {
                Button(onClick = disconnect, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(text = stringResource(R.string.bt_disconnect))
                }
                Button(onClick = toUseGlasses, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(text = stringResource(R.string.to_use_glasses))
                }
            }

        }
    }
}

@Composable
fun BluetoothDeviceItem(item: DeviceItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, fontSize = 16.sp)
            Text(text = item.macAddress, fontSize = 12.sp)
        }
        Text(text = "${item.rssi} dBm", fontSize = 14.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        BluetoothInitScreen(
            reconnect = {},
            viewModel = viewModel { BluetoothIniViewModel() },
            scan = {},
            onItemClicked = {},
            onToast = {},
            clear = {},
            doAfterConnected = {},
            disconnect = {},
            toUseGlasses = {}
        )
    }
}