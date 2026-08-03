package com.rokid.cxrmsamples.comm

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * 眼镜 PCM（16kHz 16bit mono）+ Vosk 离线 ASR。
 * 模型：assets/vosk-model-small-cn-0.22
 */
class GlassesVoskAsrEngine(
    private val context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "GlassesVoskAsrEngine"
        const val MODEL_ASSET_DIR = "vosk-model-small-cn-0.22"
        private const val SAMPLE_RATE = 16000f
        private const val COPY_MARKER = ".copy_complete"

        /** 复制完成后必须存在的关键文件 */
        private val REQUIRED_FILES = arrayOf(
            "am/final.mdl",
            "graph/Gr.fst",
            "graph/HCLr.fst",
            "ivector/final.ie",
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var model: Model? = null

    @Volatile
    private var recognizer: Recognizer? = null

    @Volatile
    private var loading = false

    private val modelDirOnDisk: File
        get() = File(context.filesDir, "vosk-model/$MODEL_ASSET_DIR")

    fun tryLoad(onReady: (Boolean) -> Unit) {
        if (model != null) {
            onReady(true)
            return
        }
        if (loading) return
        loading = true

        try {
            val modelFiles = context.assets.list(MODEL_ASSET_DIR)
            Log.i(TAG, "assets/$MODEL_ASSET_DIR entries: ${modelFiles?.joinToString() ?: "null"}")
            if (modelFiles.isNullOrEmpty()) {
                failLoad("assets/$MODEL_ASSET_DIR was not found in the APK. Clean and reinstall the app.", onReady)
                return
            }
        } catch (e: IOException) {
            failLoad("Unable to read assets/$MODEL_ASSET_DIR: ${e.message}", onReady)
            return
        }

        notifyError("Preparing the Vosk model. First launch may take 10–30 seconds…")

        Thread {
            try {
                ensureModelCopiedToDisk()
                val targetDir = modelDirOnDisk
                Log.i(TAG, "loading Vosk Model from ${targetDir.absolutePath}")
                val loadedModel = Model(targetDir.absolutePath)
                val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE)

                mainHandler.post {
                    model = loadedModel
                    recognizer = loadedRecognizer
                    loading = false
                    Log.i(TAG, "Vosk model loaded OK")
                    onReady(true)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "load model failed: ${t.javaClass.simpleName}: ${t.message}", t)
                // 清除可能损坏的缓存，下次重试会重新复制
                try {
                    modelDirOnDisk.deleteRecursively()
                } catch (_: Throwable) {
                }
                mainHandler.post {
                    val hint = when (t) {
                        is UnsatisfiedLinkError, is LinkageError ->
                            "Vosk native library failed to load (${t.message}). Verify the phone ABI and reinstall the app."
                        else ->
                            "Vosk model failed to load (${t.javaClass.simpleName}): ${t.message}"
                    }
                    failLoad(hint, onReady)
                }
            }
        }.start()
    }

    private fun ensureModelCopiedToDisk() {
        val targetDir = modelDirOnDisk
        val marker = File(targetDir, COPY_MARKER)
        if (marker.exists() && verifyModelFiles(targetDir)) {
            Log.i(TAG, "reuse cached model at ${targetDir.absolutePath}")
            return
        }

        Log.i(TAG, "copying assets/$MODEL_ASSET_DIR -> ${targetDir.absolutePath}")
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        copyAssetPath(context.assets, MODEL_ASSET_DIR, targetDir)
        marker.writeText(MODEL_ASSET_DIR)

        if (!verifyModelFiles(targetDir)) {
            val missing = REQUIRED_FILES.filter { !File(targetDir, it).exists() }
            throw IOException("Model copy incomplete. Missing: ${missing.joinToString()}")
        }
        Log.i(TAG, "model copy complete, files OK")
    }

    private fun verifyModelFiles(dir: File): Boolean {
        return REQUIRED_FILES.all { relative ->
            val f = File(dir, relative)
            f.exists() && f.length() > 0L
        }
    }

    private fun failLoad(message: String, onReady: (Boolean) -> Unit) {
        loading = false
        onError(message)
        onReady(false)
    }

    private fun notifyError(message: String) {
        mainHandler.post { onError(message) }
    }

    /**
     * 递归复制 assets。Android 对部分小文件 list() 会返回空数组而非 null，
     * 必须先尝试 open() 再当作目录处理，否则会导致模型文件缺失。
     */
    private fun copyAssetPath(assetManager: AssetManager, assetPath: String, dest: File) {
        val children = assetManager.list(assetPath)
        if (children == null || children.isEmpty()) {
            try {
                assetManager.open(assetPath).use { input ->
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                return
            } catch (_: IOException) {
                dest.mkdirs()
                return
            }
        }
        dest.mkdirs()
        for (name in children) {
            copyAssetPath(assetManager, "$assetPath/$name", File(dest, name))
        }
    }

    fun acceptPcm(data: ByteArray?, offset: Int, size: Int) {
        val rec = recognizer ?: return
        if (data == null || size <= 0) return
        val chunk = if (offset == 0 && size == data.size) data else data.copyOfRange(offset, offset + size)
        try {
            if (rec.acceptWaveForm(chunk, chunk.size)) {
                parseAndEmit(rec.result, final = true)
            } else {
                parseAndEmit(rec.partialResult, final = false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "acceptWaveForm: ${t.message}")
        }
    }

    fun resetRecognizer() {
        try {
            model?.let { recognizer = Recognizer(it, SAMPLE_RATE) }
        } catch (t: Throwable) {
            Log.w(TAG, "resetRecognizer: ${t.message}")
        }
    }

    fun release() {
        try {
            recognizer?.close()
        } catch (_: Throwable) {
        }
        recognizer = null
        try {
            model?.close()
        } catch (_: Throwable) {
        }
        model = null
    }

    private fun parseAndEmit(json: String, final: Boolean) {
        try {
            val text = JSONObject(json).optString("text", "").trim()
            if (text.isEmpty()) return
            if (final) onFinalText(text) else onPartialText(text)
        } catch (t: Throwable) {
            Log.w(TAG, "parse json failed: $json")
        }
    }
}
