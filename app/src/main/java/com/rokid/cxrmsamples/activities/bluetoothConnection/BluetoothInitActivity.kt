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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
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
    LaunchedEffect(connected.value) {
        if (connected.value) doAfterConnected()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.glasses_bg),
            modifier = Modifier.fillMaxSize(),
            alpha = 0.08f,
            contentDescription = null
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Connect Glasses",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (connected.value) {
                    "Connected"
                } else if (scanning.value) {
                    "Scanning for nearby devices…"
                } else {
                    "Select a saved or nearby device"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
            )
            if (recordState.value) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.is_record),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(recordName.value ?: "Unknown device", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = recordMacAddress.value ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = recordUuid.value ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!connected.value) {
                            OutlinedButton(onClick = reconnect, modifier = Modifier.fillMaxWidth()) {
                                Text(text = stringResource(R.string.reconnect))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!connected.value) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = scan,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = if (!scanning.value) {
                                stringResource(R.string.scan)
                            } else {
                                stringResource(R.string.stop_scan)
                            }
                        )
                    }
                    if (devices.value.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = clear,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(text = stringResource(R.string.clear_items))
                        }
                    }
                }
            }

            Text(
                text = "Nearby devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)
            )

            if (devices.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (scanning.value) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = "No devices found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices.value) { deviceItem ->
                        BluetoothDeviceItem(
                            item = deviceItem,
                            onClick = {
                                if (!connecting.value) onItemClicked(deviceItem) else onToast()
                            }
                        )
                    }
                }
            }

            if (connected.value) {
                OutlinedButton(onClick = disconnect, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.bt_disconnect))
                }
                Button(onClick = toUseGlasses, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(text = stringResource(R.string.to_use_glasses))
                }
            }

        }
    }
}

@Composable
fun BluetoothDeviceItem(item: DeviceItem, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = item.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "${item.rssi} dBm", style = MaterialTheme.typography.labelSmall)
        }
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
