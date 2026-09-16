package com.rokid.cxrmsamples.comm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.AudioStreamListener
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PictureActivity 前台语音拍照：优先眼镜麦克风 + Vosk；仅 Vosk 不可用时才尝试手机系统语音。
 */
class PictureVoiceController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val onTakePhoto: () -> Unit,
    private val onStatusChanged: (String) -> Unit,
) {
    companion object {
        private const val TAG = "PictureVoiceController"
        private const val GLASSES_AUDIO_STREAM = "picture_voice"
        private const val RESTART_LISTEN_DELAY_MS = 500L
        private const val AFTER_COMMAND_COOLDOWN_MS = 2000L

        const val VOICE_HINT = "analyze image / 拍照"

        private val COMMAND_ALIASES = listOf(
            "analyze image",
            "analyse image",
            "analyze photo",
            "start analysis",
            "识别图片",
            "开始识别",
            "分析图片",
            "拍照",
        )
    }

    enum class AsrSource {
        GLASSES_VOSK,
        PHONE_SYSTEM,
        NONE,
    }

    @Volatile
    private var active = false

    @Volatile
    private var asrSource = AsrSource.NONE

    @Volatile
    private var voskLoaded = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var restartListenJob: Job? = null
    private var lastCommandAtMs = 0L
    private var glassesAudioActive = false
    private var lastVoskError: String? = null

    private var voskEngine: GlassesVoskAsrEngine? = null

    private val audioStreamListener = object : AudioStreamListener {
        override fun onStartAudioStream(codeType: Int, streamType: String?) {
            glassesAudioActive = true
            Log.i(TAG, "glasses mic stream ON: codeType=$codeType name=$streamType")
            tryActivateGlassesVosk()
        }

        override fun onAudioStream(data: ByteArray?, offset: Int, size: Int) {
            if (asrSource == AsrSource.GLASSES_VOSK) {
                voskEngine?.acceptPcm(data, offset, size)
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!active || asrSource != AsrSource.PHONE_SYSTEM) return
            Log.w(TAG, "SpeechRecognizer error=$error")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    onStatusChanged("Phone microphone listening. Say: $VOICE_HINT")
                    scheduleRestartPhoneListen(RESTART_LISTEN_DELAY_MS)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestartPhoneListen(800)
                else -> {
                    onStatusChanged("Phone speech recognition error ($error). Retrying…")
                    scheduleRestartPhoneListen(1000)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (asrSource != AsrSource.PHONE_SYSTEM) return
            handleAsrText(results, final = true)
            if (active) scheduleRestartPhoneListen(AFTER_COMMAND_COOLDOWN_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (asrSource != AsrSource.PHONE_SYSTEM) return
            handleAsrText(partialResults, final = false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        if (active) return
        active = true
        voskLoaded = false
        lastVoskError = null
        asrSource = AsrSource.NONE
        onStatusChanged("Loading Vosk model (${GlassesVoskAsrEngine.MODEL_ASSET_DIR})…")
        startGlassesAudioCapture()

        voskEngine = GlassesVoskAsrEngine(
            context = appContext,
            onPartialText = { text -> onVoskText(text, final = false) },
            onFinalText = { text -> onVoskText(text, final = true) },
            onError = { msg ->
                lastVoskError = msg
                Log.w(TAG, "Vosk error: $msg")
                if (active && !voskLoaded) {
                    onStatusChanged(msg)
                }
            },
        )

        voskEngine?.tryLoad { voskReady ->
            if (!active) return@tryLoad
            if (voskReady) {
                voskLoaded = true
                Log.i(TAG, "Vosk model loaded (${GlassesVoskAsrEngine.MODEL_ASSET_DIR})")
                tryActivateGlassesVosk()
            } else {
                val detail = lastVoskError ?: "Verify assets/${GlassesVoskAsrEngine.MODEL_ASSET_DIR} is packaged in the APK"
                Log.e(TAG, "Vosk load failed: $detail")
                startPhoneFallback("Vosk model failed to load: $detail")
            }
        }
    }

    /** Vosk 已加载且眼镜 PCM 已连通时启用眼镜麦识别 */
    private fun tryActivateGlassesVosk() {
        if (!active || !voskLoaded) return
        if (!glassesAudioActive) {
            onStatusChanged("Vosk is ready. Waiting for the glasses microphone…")
            return
        }
        asrSource = AsrSource.GLASSES_VOSK
        onStatusChanged("Glasses microphone listening. Say: $VOICE_HINT")
        Log.i(TAG, "using glasses mic + Vosk ASR")
    }

    private fun startPhoneFallback(reason: String) {
        val check = SpeechEngineHelper.check(appContext)
        if (!check.available) {
            // 保留 Vosk 相关原因，不要只显示「系统语音引擎不可用」
            onStatusChanged(
                buildString {
                    append(reason)
                    append("\n")
                    append("Phone speech recognition is also unavailable. ")
                    append("Check the GlassesVoskAsrEngine and PictureVoiceController Logcat tags.")
                }
            )
            Log.e(TAG, "phone fallback unavailable. reason=$reason check=${check.userMessage}")
            return
        }
        asrSource = AsrSource.PHONE_SYSTEM
        speechRecognizer = SpeechEngineHelper.createRecognizer(appContext)
        if (speechRecognizer == null) {
            onStatusChanged("$reason\nUnable to create the phone speech recognizer.")
            return
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)
        onStatusChanged("$reason\nUsing the phone microphone. Say: $VOICE_HINT")
        Log.i(TAG, "using phone mic + SpeechRecognizer. ${check.userMessage}")
        startPhoneListening()
    }

    fun stop() {
        if (!active) return
        active = false
        asrSource = AsrSource.NONE
        voskLoaded = false
        restartListenJob?.cancel()
        restartListenJob = null
        stopPhoneListening()
        stopGlassesAudioCapture()
        speechRecognizer?.destroy()
        speechRecognizer = null
        voskEngine?.release()
        voskEngine = null
        onStatusChanged("")
        Log.i(TAG, "voice control stopped")
    }

    private fun onVoskText(text: String, final: Boolean) {
        Log.i(TAG, "Vosk ${if (final) "final" else "partial"}: $text")
        if (final) {
            tryMatchCommand(text)
            voskEngine?.resetRecognizer()
        } else if (matchesTakePhotoCommand(text)) {
            tryMatchCommand(text)
        }
    }

    private fun handleAsrText(bundle: Bundle?, final: Boolean) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (texts.isEmpty()) return
        val joined = texts.joinToString(separator = "")
        Log.i(TAG, "Phone ASR ${if (final) "final" else "partial"}: $joined")
        if (final) tryMatchCommand(joined)
    }

    private fun tryMatchCommand(text: String) {
        if (!matchesTakePhotoCommand(text)) return
        val now = System.currentTimeMillis()
        if (now - lastCommandAtMs < AFTER_COMMAND_COOLDOWN_MS) return
        lastCommandAtMs = now
        onStatusChanged("Command recognized. Taking photo…")
        onTakePhoto()
        scope.launch {
            delay(1500)
            if (!active) return@launch
            when (asrSource) {
                AsrSource.GLASSES_VOSK ->
                    onStatusChanged("Glasses microphone listening. Say: $VOICE_HINT")
                AsrSource.PHONE_SYSTEM ->
                    onStatusChanged("Phone microphone listening. Say: $VOICE_HINT")
                AsrSource.NONE -> tryActivateGlassesVosk()
            }
        }
    }

    private fun matchesTakePhotoCommand(text: String): Boolean {
        val normalized = text
            .replace(" ", "")
            .replace("，", "")
            .replace(",", "")
            .replace("。", "")
            .replace("乐奇", "")
            .lowercase()
        return COMMAND_ALIASES.any { alias ->
            normalized.contains(alias.replace(" ", ""))
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
        }
    }

    private fun startPhoneListening() {
        if (!active || asrSource != AsrSource.PHONE_SYSTEM) return
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(buildRecognizerIntent())
        } catch (t: Throwable) {
            Log.w(TAG, "startPhoneListening failed: ${t.message}")
            onStatusChanged("Unable to start phone speech recognition")
            scheduleRestartPhoneListen(1500)
        }
    }

    private fun stopPhoneListening() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun scheduleRestartPhoneListen(delayMs: Long) {
        restartListenJob?.cancel()
        restartListenJob = scope.launch {
            delay(delayMs)
            if (active && asrSource == AsrSource.PHONE_SYSTEM) {
                startPhoneListening()
            }
        }
    }

    private fun startGlassesAudioCapture() {
        glassesAudioActive = false
        try {
            CxrApi.getInstance().setAudioStreamListener(audioStreamListener)
            when (CxrApi.getInstance().openAudioRecord(1, GLASSES_AUDIO_STREAM)) {
                ValueUtil.CxrStatus.REQUEST_SUCCEED ->
                    Log.i(TAG, "openAudioRecord($GLASSES_AUDIO_STREAM) succeed")
                ValueUtil.CxrStatus.REQUEST_WAITING ->
                    Log.i(TAG, "openAudioRecord waiting")
                else ->
                    Log.w(TAG, "openAudioRecord failed — check bluetooth connection")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startGlassesAudioCapture: ${t.message}")
        }
    }

    private fun stopGlassesAudioCapture() {
        glassesAudioActive = false
        try {
            CxrApi.getInstance().closeAudioRecord(GLASSES_AUDIO_STREAM)
        } catch (_: Throwable) {
        }
        try {
            CxrApi.getInstance().setAudioStreamListener(null)
        } catch (_: Throwable) {
        }
    }
}
