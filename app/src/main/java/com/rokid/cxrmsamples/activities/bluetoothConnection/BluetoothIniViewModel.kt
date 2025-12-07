package com.rokid.cxrmsamples.activities.bluetoothConnection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.BluetoothStatusCallback
import com.rokid.cxr.client.utils.ValueUtil
import com.rokid.cxrmsamples.activities.usageSelection.UsageSelectionActivity
import com.rokid.cxrmsamples.dataBeans.CONSTANT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceItem(
    val device: BluetoothDevice?,
    val name: String,
    val macAddress: String,
    val rssi: Int
)

class BluetoothIniViewModel : ViewModel() {

    private val TAG = "BluetoothIniViewModel"

    private val _recordState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recordState: StateFlow<Boolean> = _recordState.asStateFlow()

    private val isScanning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = isScanning.asStateFlow()

    private val _devicesList: MutableStateFlow<List<DeviceItem>> = MutableStateFlow(emptyList())
    val devicesList: StateFlow<List<DeviceItem>> = _devicesList.asStateFlow()

    private val _recordName: MutableStateFlow<String?> = MutableStateFlow(null)
    val recordName: StateFlow<String?> = _recordName.asStateFlow()

    private val _recordUUID: MutableStateFlow<String?> = MutableStateFlow(null)
    val recordUUID: StateFlow<String?> = _recordUUID.asStateFlow()

    private val _recordMacAddress: MutableStateFlow<String?> = MutableStateFlow(null)
    val recordMacAddress: StateFlow<String?> = _recordMacAddress.asStateFlow()

    private val _connecting: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _connected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val toConnect = MutableLiveData<Boolean>()

    // Bluetooth connection state callback
    private val connectionState = object : BluetoothStatusCallback {
        /**
         * Called when a Bluetooth device connects or disconnects
         * @param uuid The UUID of the device
         * @param macAddress The MAC address of the device
         * @param p2 rokid account, do not use it if you don't need it
         * @param glassesType Type of glasses, 0 -- non-display, 1 -- have display
         */
        override fun onConnectionInfo(
            uuid: String?,
            macAddress: String?,
            p2: String?,
            glassesType: Int
        ) {
            Log.d(TAG, "onConnectionInfo: uuid=$uuid, macAddress=$macAddress, p2=$p2, p3=${if (glassesType == 1) "Display glasses" else "Non-display glasses"}")
            // Check if device info matches recorded info
            if (_recordUUID.value == (uuid ?: "error") && _recordMacAddress.value == (macAddress
                    ?: "error")
            ) {
                Log.d(TAG, "Device matches recorded info")
                // If device is not connected, post toConnect
                if (!CxrApi.getInstance().isBluetoothConnected) {
                    Log.d(TAG, "Device not connected, posting toConnect")
                    toConnect.postValue(true)
                } else {
                    Log.d(TAG, "Device already connected")
                }
            } else { // If device info does not match, update records and post toConnect
                Log.d(TAG, "New device info received")
                uuid?.let { u ->
                    macAddress?.let { m ->
                        Log.d(TAG, "Updating records and posting toConnect")
                        _recordUUID.value = u
                        _recordMacAddress.value = m
                        toConnect.postValue(true)
                    }
                }
            }
        }

        /**
         * Called when a Bluetooth device connects successfully
         *
         */
        override fun onConnected() {
            Log.d(TAG, "Bluetooth device connected successfully")
            _devicesList.value = emptyList()
            _connected.value = true
            _connecting.value = false
        }

        /**
         * Called when a Bluetooth device disconnects
         *
         */
        override fun onDisconnected() {
            Log.d(TAG, "Bluetooth device disconnected")
            _connecting.value = false
            _connected.value = false
        }

        /**
         * Called when a Bluetooth device connection fails
         * @param p0 The error code:
         *  @see ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID  Invalid parameter
         *  @see ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED Bluetooth connection failed
         *  @see ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED Socket connection failed
         *  @see ValueUtil.CxrBluetoothErrorCode.SN_CHECK_FAILED Serial number check failed
         *  @see ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown error
         */
        override fun onFailed(p0: ValueUtil.CxrBluetoothErrorCode?) {
            Log.e(TAG, "Bluetooth connection failed with error: $p0")
            _connecting.value = false
            _connected.value = false
        }

    }

    init {
        _recordState.value = false
        // Add some sample devices for testing
//        _devicesList.value = listOf(
//            DeviceItem(null,"Device 1", "00:11:22:33:44:55", -45),
//            DeviceItem(null, "Device 2", "AA:BB:CC:DD:EE:FF", -60),
//            DeviceItem(null, "Device 3", "12:34:56:78:90:AB", -75)
//        )
    }
    // Callback for BLE scan
    private val bleScannerCallback: ScanCallback = object : ScanCallback() {
        /**
         * Called when a BLE device is found
         *
         */
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val name = device.name ?: "Unknown"
            val macAddress = device.address
            val rssi = result.rssi
            Log.d(TAG, "Found BLE device: name=$name, address=$macAddress, rssi=$rssi")
            addDevice(device, rssi)
        }

        /**
         * Called when a BLE scan fails
         *
         */
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }

    /**
     * Handle BLE scanning status, if it is currently scanning, stop it, otherwise start a new scan
     * @param bleScanner The BluetoothLeScanner instance
     */
    @SuppressLint("MissingPermission")
    fun handleScan(bleScanner: BluetoothLeScanner?) {
        if (isScanning.value) {
            Log.d(TAG, "Stopping BLE scan")
            bleScanner?.stopScan(bleScannerCallback)
            isScanning.value = false
        } else {
            Log.d(TAG, "Starting BLE scan with service UUID: ${CONSTANT.SERVICE_UUID}")
            // Create a filter to match devices with the specified service UUID
            val filter =
                ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(CONSTANT.SERVICE_UUID))
                    .build()
            val scanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bleScanner?.startScan(mutableListOf(filter), scanSettings, bleScannerCallback)
            isScanning.value = true
        }
    }

    /**
     * Check if a record exists
     */
    fun checkRecordState(context: Context) {
        Log.d(TAG, "Checking record state")
        // Read record from shared preferences
        val sharedPreferences = context.getSharedPreferences("record", Context.MODE_PRIVATE)
        val recordName = sharedPreferences.getString("record_name", null)
        val recordUUID = sharedPreferences.getString("record_uuid", null)
        val recordMacAddress = sharedPreferences.getString("record_mac_address", null)
        recordName?.let { name ->
            recordUUID?.let { uuid ->
                recordMacAddress?.let { mac ->
                    Log.d(TAG, "Record found: name=$name, uuid=$uuid, mac=$mac")
                    this._recordName.value = name
                    this._recordUUID.value = uuid
                    this._recordMacAddress.value = mac
                    _recordState.value = true
                    _connecting.value = false
                    return
                }
            }
        }
        Log.d(TAG, "No record found")
        _recordState.value = false
    }

    /**
     * To record device info when a connection is successful
     */
    fun record(context: Context) {
        Log.d(
            TAG,
            "Recording device info: name=${_recordName.value}, uuid=${_recordUUID.value}, mac=${_recordMacAddress.value}"
        )
        val sharedPreferences = context.getSharedPreferences("record", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putString("record_name", _recordName.value)
            putString("record_uuid", _recordUUID.value)
            putString("record_mac_address", _recordMacAddress.value)
        }
        _recordState.value = true
    }


    /**
     * Add bluetooth device to the found list
     * @param device The BluetoothDevice instance
     * @param rssi The RSSI value
     */
    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice, rssi: Int) {
        Log.d(
            TAG,
            "Adding device: name=${device.name ?: "Unknown"}, address=${device.address}, rssi=$rssi"
        )
        // Check if th device found is  already in the found device list
        val existingDevice = _devicesList.value.find { it.device == device }
        if (existingDevice != null) {
            Log.d(TAG, "Device already exists, updating RSSI")
            updateRssi(device, rssi)
        } else {
            val newDevice = DeviceItem(device, device.name ?: "Unknown", device.address, rssi)
            _devicesList.value += newDevice
            Log.d(TAG, "New device added to list, total devices: ${_devicesList.value.size}")
        }
    }

    /**
     * Update the RSSI value for a device if it exists in the list
     * @param device The BluetoothDevice instance
     * @param rssi The new RSSI value
     */
    fun updateRssi(device: BluetoothDevice, rssi: Int) {
        Log.d(TAG, "Updating RSSI for device ${device.address}: $rssi")
        _devicesList.value = _devicesList.value.map {
            if (it.device == device) {
                it.copy(rssi = rssi)
            } else {
                it
            }
        }
    }

    /**
     * Clear the device list
     */
    fun clearDevices() {
        Log.d(TAG, "Clearing device list")
        _devicesList.value = emptyList()
    }

    /**
     * Connect to Glasses's socket, the last step of the connection process
     */
    fun connectBTSocket(context: Context) {
        Log.d(
            TAG,
            "Reconnecting to device: uuid=${_recordUUID.value}, mac=${_recordMacAddress.value}"
        )
        // Reconnect/Connect(first time) to the device
        try {
            CxrApi.getInstance().connectBluetooth(
                context,
                _recordUUID.value ?: "error", // uuid from record or BluetoothStatusCallback::onConnectionInfo
                _recordMacAddress.value ?: "error", // mac from record or BluetoothStatusCallback::onConnectionInfo
                connectionState, // callback for connection state
                readRawFile(context), // SN authentication file
                CONSTANT.CLIENT_SECRET.replace("-", "") // client secret
            )
        }catch (e: Exception){
            Log.d(TAG, "Error: ${e.message}")
            e.printStackTrace()
        }

    }


    /**
     * Init Bluetooth connection after a device in fount device list is clicked
     * @param deviceItem The selected device item
     */
    fun deviceClicked(context: Context, deviceItem: DeviceItem?) {
        deviceItem?.let {
            Log.d(TAG, "Device clicked: name=${it.name}, address=${it.macAddress}")
            _recordName.value = it.name
            CxrApi.getInstance().initBluetooth(context, it.device, connectionState)
            _connecting.value = true
        }
    }

    /**
     * Read the SN authentication file
     */
    @Throws(Exception::class)
    fun readRawFile(context: Context): ByteArray{
        Log.d(TAG, "Reading raw file")
        try {
            val inputStream =
                context.resources.openRawResource(CONSTANT.getSNResource())
            val bytes = inputStream.readBytes()
            Log.d(TAG, "Read ${bytes.size} bytes from raw file")
            return bytes
        }catch (e: Exception){
            Log.e(TAG, "Error reading raw file: ${e.message}")
            throw Exception("Error reading raw file")
        }
    }

    /**
     * Disconnect from the Bluetooth socket
     */
    fun disconnect() {
        CxrApi.getInstance().deinitBluetooth()
    }
    /**
     * Switch to the usage selection activity
     */
    fun toUseGlasses(context: Context){
        context.startActivity(Intent(context, UsageSelectionActivity::class.java))
    }
    /**
     * Check the connection status
     */
    fun checkConnection() {
        _connected.value = CxrApi.getInstance().isBluetoothConnected
    }
}