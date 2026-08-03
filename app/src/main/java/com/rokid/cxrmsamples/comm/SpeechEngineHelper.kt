package com.rokid.cxrmsamples.comm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 检测并创建系统 [SpeechRecognizer]（使用手机麦克风，依赖系统语音引擎）。
 */
object SpeechEngineHelper {
    private const val TAG = "SpeechEngineHelper"

    data class Availability(
        val available: Boolean,
        val userMessage: String,
        val installedEngines: List<String>,
    )

    fun check(context: Context): Availability {
        val engines = queryRecognitionEngines(context)
        val flagAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val intentResolvable = context.packageManager.resolveActivity(
            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null

        val available = flagAvailable || engines.isNotEmpty() || intentResolvable

        val engineNames = engines.map { it.serviceInfo.packageName }.distinct()
        val message = when {
            available -> "Phone speech engine available: ${engineNames.joinToString()}"
            else -> buildUnavailableMessage(engineNames)
        }

        Log.i(
            TAG,
            "check: flag=$flagAvailable intent=$intentResolvable engines=$engines sdk=${Build.VERSION.SDK_INT}"
        )

        return Availability(available, message, engineNames)
    }

    fun createRecognizer(context: Context): SpeechRecognizer? {
        val engines = queryRecognitionEngines(context)
        return try {
            if (engines.isNotEmpty()) {
                val service = engines.first().serviceInfo
                val component = android.content.ComponentName(service.packageName, service.name)
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext, component)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createRecognizer failed: ${t.message}", t)
            null
        }
    }

    private fun queryRecognitionEngines(context: Context): List<android.content.pm.ResolveInfo> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    private fun buildUnavailableMessage(engineNames: List<String>): String {
        return buildString {
            append("No speech recognition engine is available. ")
            append("Android SpeechRecognizer requires a system speech service, commonly Google Speech Services.")
            if (engineNames.isEmpty()) {
                append(" Install or update the Google app, enable voice input in system settings, ")
                append("or verify assets/vosk-model-small-cn-0.22 is packaged before reinstalling the app.")
            }
        }
    }
}
