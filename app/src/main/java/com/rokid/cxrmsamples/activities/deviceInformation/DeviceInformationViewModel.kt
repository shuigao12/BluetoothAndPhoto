package com.rokid.cxrmsamples.activities.deviceInformation

import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.GlassInfoResultCallback
import com.rokid.cxr.client.extend.listeners.BatteryLevelUpdateListener
import com.rokid.cxr.client.extend.listeners.BrightnessUpdateListener
import com.rokid.cxr.client.extend.listeners.ScreenStatusUpdateListener
import com.rokid.cxr.client.extend.listeners.VolumeUpdateListener
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceInformationViewModel: ViewModel() {

    private val TAG = "DeviceInformationViewModel"

    private val _batteryLevel : MutableStateFlow<Int> = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()
    private val _isCharging : MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val isCharging: StateFlow<Boolean?> = _isCharging.asStateFlow()

    private val _brightness : MutableStateFlow<Int> = MutableStateFlow(-1)
    val brightness: StateFlow<Int> = _brightness.asStateFlow()

    private val _soundVolume : MutableStateFlow<Int> = MutableStateFlow(-1)
    val soundVolume: StateFlow<Int> = _soundVolume.asStateFlow()

    private val _wearingState : MutableStateFlow<String?> = MutableStateFlow(null)
    val wearingState: StateFlow<String?> = _wearingState.asStateFlow()

    private val _deviceId : MutableStateFlow<String?> = MutableStateFlow(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    private val _systemVersion : MutableStateFlow<String?> = MutableStateFlow(null)
    val systemVersion: StateFlow<String?> = _systemVersion.asStateFlow()

    private val _deviceName : MutableStateFlow<String?> = MutableStateFlow(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _isScreenOn : MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val isScreenOn: StateFlow<Boolean?> = _isScreenOn.asStateFlow()

    private val _volumeListenerSet : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val volumeListenerSet: StateFlow<Boolean> = _volumeListenerSet.asStateFlow()

    private val _brightnessListenerSet : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val brightnessListenerSet: StateFlow<Boolean> = _brightnessListenerSet.asStateFlow()

    private val _screenListenerSet : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val screenListenerSet: StateFlow<Boolean> = _screenListenerSet.asStateFlow()

    private val _setVolume : MutableStateFlow<Int> = MutableStateFlow(10)
    val setVolume: StateFlow<Int> = _setVolume.asStateFlow()

    private val _setBrightness : MutableStateFlow<Int> = MutableStateFlow(10)
    val setBrightness: StateFlow<Int> = _setBrightness.asStateFlow()

    private val _batteryListenerSet : MutableStateFlow<Boolean> = MutableStateFlow(false)
    val batteryListenerSet: StateFlow<Boolean> = _batteryListenerSet.asStateFlow()
    // Get Glass Information
    private val glassInfoCallback = GlassInfoResultCallback { status, glassInfo ->
        if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED){
            glassInfo?.let {info ->
                _deviceName.value = info.deviceName // Device Name
                _deviceId.value = info.deviceId // Device ID
                _systemVersion.value = info.systemVersion // System Version
                _wearingState.value = info.wearingStatus  // Wearing State
                _brightness.value = info.brightness // Brightness
                _setBrightness.value = info.brightness
                _soundVolume.value = info.volume // Sound Volume
                _setVolume.value = info.volume
                _batteryLevel.value = info.batteryLevel // Battery Level
                _isCharging.value = info.isCharging // Is Charging
                Log.i(TAG, "glassInfo = $info")
            }
        }else{
            Log.e(TAG, "getGlassInfo failed: ${status.name}")
        }
    }
    /**
     * Get All Device Information
     */
    fun getDeviceInformation(){
        CxrApi.getInstance().getGlassInfo(glassInfoCallback)
    }

    /**
     * Listen for battery level changes
     *
     * @param toSet true: Set listener; false: Cancel listener
     */
    fun toSetBatteryListener(){
        val toSet = !_batteryListenerSet.value
        CxrApi.getInstance().setBatteryLevelUpdateListener(if (toSet)  {
            BatteryLevelUpdateListener { level, isCharging ->
                _batteryLevel.value = level // Battery Level
                _isCharging.value = isCharging // Is Charging
            }
        } else null)
        _batteryListenerSet.value = toSet
    }

    /**
     * Listen for brightness changes
     *
     */
    fun toSetBrightnessListener(){
        val toSet = !_brightnessListenerSet.value // true: Set listener, false: Cancel listener
        CxrApi.getInstance().setBrightnessUpdateListener (if (toSet) {
            BrightnessUpdateListener { level ->
                _brightness.value = level // Brightness
                _setBrightness.value = level // Set Brightness
            }
        } else{
            _brightness.value = -1
            null
        })
        _brightnessListenerSet.value = toSet
    }

    /**
     * Set brightness
     * @param level brightness level, Range [0, 15]
     */
    fun setBrightness(level: Int){
        // Validate brightness level, level must be in range [0, 15]
        val validLevel = level.coerceIn(0, 15)
        // Set brightness
        when(CxrApi.getInstance().setGlassBrightness(validLevel)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {// Set brightness succeed
                Log.i(TAG, "setBrightness succeed")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {// Set brightness failed
                Log.e(TAG, "setBrightness failed")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {// Set brightness requested, but Glass is busy
                Log.e(TAG, "Set Brightness Command is in queue!")
            }
            else -> {// Unknown status
                Log.e(TAG, "Unknown status")
            }
        }
    }

    /**
     * Listen for volume changes
     *
     */
    fun toSetSoundVolumeListener(){
        val toSet = !_volumeListenerSet.value // true: Set listener, false: Cancel listener
        CxrApi.getInstance().setVolumeUpdateListener(if (toSet) {
            VolumeUpdateListener { level ->
                _soundVolume.value = level // Sound Volume
                _setVolume.value = level // Set Volume
            }
        } else{
            _soundVolume.value = -1
            null
        })
        _volumeListenerSet.value = toSet
    }

    /**
     * Set volume
     * @param level volume level Range(0-15)
     */
    fun setSoundVolume(level: Int){
        val validLevel = level.coerceIn(0, 15) // Validate volume level, level must be in range [0, 15]
        when(CxrApi.getInstance().setGlassVolume(validLevel)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // Set volume succeed
                Log.i(TAG, "setSoundVolume succeed")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> { // Set volume failed
                Log.e(TAG, "setSoundVolume failed")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> { // Set volume requested, but Glass is busy
                Log.e(TAG, "Set Volume Command is in queue!")
            }
            else -> { // Unknown status
                Log.e(TAG, "Unknown status")
            }
        }
    }

    /**
     * Listen for screen status changes
     *
     */
    fun toSetScreenListener(){
        val toSet = !_screenListenerSet.value // true: Set listener, false: Cancel listener
        CxrApi.getInstance().setScreenStatusUpdateListener(if (toSet) {
            ScreenStatusUpdateListener { isScreenOn ->
                _isScreenOn.value = isScreenOn // Screen Status
                _screenListenerSet.value = true
            }
        } else{
            _screenListenerSet.value = false
            null
        })
        _screenListenerSet.value = toSet
    }

    /**
     * Notify glasses to turn off screen
     */
    fun notifyScreenOff(){
        when(CxrApi.getInstance().notifyGlassScreenOff()){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> { // Notify screen off succeed
                Log.i(TAG, "notifyScreenOff succeed")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> { // Notify screen off failed
                Log.e(TAG, "notifyScreenOff failed")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> { // Notify screen off requested, but Glass is busy
                Log.e(TAG, "Notify Screen Off Command is in queue!")
            }
            else -> { // Unknown status
                Log.e(TAG, "Unknown status")
            }
        }
    }

    fun volumeUp() {
        val current = _setVolume.value
        if (current >= 15) return
        _setVolume.value = current + 1
        setSoundVolume(current + 1)
    }

    fun volumeDown() {
        val current = _setVolume.value
        if (current <= 0) return
        _setVolume.value = current - 1
        setSoundVolume(current - 1)
    }

    fun brightnessUp() {
        val current = _setBrightness.value
        if (current >= 15) return
        _setBrightness.value = current + 1
        setBrightness(current + 1)
    }

    fun brightnessDown() {
        val current = _setBrightness.value
        if (current <= 0) return
        _setBrightness.value = current - 1
        setBrightness(current - 1)
    }

}