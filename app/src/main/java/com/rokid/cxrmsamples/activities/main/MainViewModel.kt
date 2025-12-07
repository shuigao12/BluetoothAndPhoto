package com.rokid.cxrmsamples.activities.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import com.rokid.cxrmsamples.activities.bluetoothConnection.BluetoothInitActivity
import com.rokid.cxrmsamples.dataBeans.CONSTANT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BluetoothState {
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    BLUETOOTH_READY,
}

class MainViewModel : ViewModel() {
    private val _bluetoothState = MutableStateFlow(BluetoothState.PERMISSION_REQUIRED)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    init {
        _bluetoothState.value = BluetoothState.PERMISSION_REQUIRED
    }

    /**
     * Check bluetooth enabled
     * @param bluetoothAdapter BluetoothAdapter
     */
    fun checkBluetoothEnabled(bluetoothAdapter: BluetoothAdapter?) {
        _bluetoothState.value = bluetoothAdapter?.let {
            if (it.isEnabled) {
                BluetoothState.BLUETOOTH_READY
            } else {
                BluetoothState.BLUETOOTH_DISABLED
            }
        } ?: run {
            BluetoothState.BLUETOOTH_DISABLED
        }
    }


    /**
     * Check bluetooth permission
     */
    fun checkPermission(context: Context, manager: BluetoothManager) {
        if (CONSTANT.BLUETOOTH_PERMISSIONS.all {
                val result = ActivityCompat.checkSelfPermission(
                    context,
                    it
                ) == PackageManager.PERMISSION_GRANTED
                Log.e("Permission", "permission = $it, result = $result")
                result
            }) {
            checkBluetoothEnabled(manager.adapter)
        } else {
            _bluetoothState.value = BluetoothState.PERMISSION_REQUIRED
        }
    }

    /**
     * Request bluetooth enable
     * @param launcher ActivityResultLauncher<Intent> To start activity for result
     */
    fun requestBluetoothEnable(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    /**
     * Request bluetooth permission
     * @param launcher ActivityResultLauncher<Array<String>> To start permission request
     */
    fun requestBluetoothPermission(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(CONSTANT.BLUETOOTH_PERMISSIONS)
    }

    /**
     * To init bluetooth
     * @param context Context
     */
    fun toInit(context: Context) {
        context.startActivity(Intent(context, BluetoothInitActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

}