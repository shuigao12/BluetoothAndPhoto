package com.rokid.cxrmsamples.activities.useAIScene

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.extend.listeners.AiEventListener
import com.rokid.cxr.client.extend.listeners.AudioStreamListener
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AISceneViewModel : ViewModel() {
    private val TAG = "AISceneViewModel"

    private val _aiSceneUiState = MutableStateFlow(false)
    val aiSceneUiState = _aiSceneUiState.asStateFlow()
    private val _hasPhotoRequest = MutableStateFlow(false)
    val hasPhotoRequest = _hasPhotoRequest.asStateFlow()
    private val _isDeviceControl = MutableStateFlow(false)
    val isDeviceControl = _isDeviceControl.asStateFlow()
    private val _photoGet: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    val photoGet = _photoGet.asStateFlow()

    // AI事件监听器
    private val aiEventListener = object : AiEventListener {
        override fun onAiKeyDown() {
            CxrApi.getInstance().setAudioStreamListener(audioStreamListener)
            startSendingHeartbeat()
            _aiSceneUiState.value = true
        }

        override fun onAiKeyUp() {
        }

        override fun onAiExit() {
            CxrApi.getInstance().setAudioStreamListener(null)
            stopSendingHeartbeat()
            _aiSceneUiState.value = false
        }
    }

    // 音频流监听器
    private val audioStreamListener = object : AudioStreamListener {
        override fun onStartAudioStream(codeType: Int, streamType: String?) {
            Log.d(TAG, "onStartAudioStream: codeType=$codeType, streamType=$streamType")
        }

        override fun onAudioStream(data: ByteArray?, offset: Int, size: Int) {
            // 处理音频流数据
            sendAudioToASR(data, offset, size)
        }
    }

    // 心跳包发送任务
    private var heartbeatJob: Job? = null

    init {
        // 在初始化时设置AI事件监听器
        CxrApi.getInstance().setAiEventListener(aiEventListener)
    }

    /**
     * 设置是否为设备控制意图
     */
    fun setDeviceControl(isDeviceControl: Boolean) {
        _isDeviceControl.value = isDeviceControl
        if(isDeviceControl){
            setHasPhotoRequest(false)
        }
    }

    /**
     * 设置是否需要图片
     */
    fun setHasPhotoRequest(hasPhotoRequest: Boolean) {
        _hasPhotoRequest.value = hasPhotoRequest
    }

    /**
     * 开始定期发送心跳包
     */
    private fun startSendingHeartbeat() {
        // 如果已经有正在运行的心跳任务，则先停止它
        heartbeatJob?.cancel()

        // 启动新的心跳任务，每3秒发送一次心跳包
        heartbeatJob = viewModelScope.launch {
            while (true) {
                CxrApi.getInstance().sendAi_Heartbeat()
                Log.d(TAG, "sendAi_Heartbeat sent")
                delay(3000) // 延迟3秒
            }
        }
    }

    /**
     * 停止发送心跳包
     */
    private fun stopSendingHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "heartbeat sending stopped")
    }

    private var count = 0
    private var byteSize = 0

    /**
     * 模拟将音频数据发送给ASR
     */
    private fun sendAudioToASR(data: ByteArray?, offset: Int, size: Int) {
        // 发送音频数据给ASR
//        data?.let {
//            if (it.size < offset + size) {
//                return
//            } else {
//                val realData = it.copyOfRange(offset, offset + size)
//            }
//        }
        // ...
        // 模拟从ASR 返回

        var result = "模拟ASR返回结果"
        count++
        byteSize += size
        if (count >= 100) {
            result = "模拟ASR返回结果 $byteSize"
            CxrApi.getInstance().sendAsrContent(result)
        }
    }

    /**
     * 模拟ASR 结束--一个按键
     */
    fun whenASREnd(){
        //发送ASR End
        CxrApi.getInstance().notifyAsrEnd()
        //模拟意图判断
        if(_isDeviceControl.value){
            CxrApi.getInstance().sendExitEvent()
        }else{
            if (_hasPhotoRequest.value){
                takeAIPic()
            }else{
                sendToAI()
            }
        }

    }

    /**
     * 模拟将文本或图片发送给 AI
     */
    private fun sendToAI() {
        // 发送文本或图片给 AI
        // ...
        // 模拟AI 响应
        val text = "模拟AI返回结果"
        // 循环发送10次，启动携程
        viewModelScope.launch {
            for (i in 1..10) {
                sendTTS(text)
                delay(1000)
            }
            // 模拟发送结束
            ttsEnd()
        }
    }
    private fun ttsEnd(){
        CxrApi.getInstance().notifyTtsAudioFinished()
        //结束AI 流程
        CxrApi.getInstance().sendExitEvent()
    }

    private fun takeAIPic(){
        CxrApi.getInstance().takeGlassPhoto(640, 480, 100, PhotoResultCallback { status, pictureData -> 
            //--见图片传递给画面
            if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED){
                pictureData?.let {
                    _photoGet.value = BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
                }
                // 模拟AI 返回
                sendToAI()
            }else{
                takeAIPic()
            }
        })
    }

    fun sendTTS(text: String){
        CxrApi.getInstance().sendTtsContent(text)
    }

    override fun onCleared() {
        super.onCleared()
        // 清理资源
        CxrApi.getInstance().setAiEventListener(null)
        CxrApi.getInstance().setAudioStreamListener(null)
        stopSendingHeartbeat()
    }

}