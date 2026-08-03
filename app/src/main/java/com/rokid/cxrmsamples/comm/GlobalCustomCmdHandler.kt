package com.rokid.cxrmsamples.comm

import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.extend.listeners.CustomCmdListener
import com.rokid.cxr.client.utils.ValueUtil
import com.rokid.cxrmsamples.dataBeans.CONSTANT
import java.util.concurrent.CopyOnWriteArraySet

/*
    GlobalCustomCmdHandler 是一个全局单例对象，负责注册到 CxrApi 以监听来自手机端的 CUSTOM_CMD 消息。
    它解析收到的 Caps 数据，检查是否包含 "take_picture" 命令
    如果检测到 "take_picture"，它会调用 PictureController.triggerRemoteTake() 来触发拍照逻辑。
    其他模块（如 UI 页面）可以通过 addObserver() 注册回调来接收解析后的命令字符串，便于调试和扩展其他命令的处理。
 */
object GlobalCustomCmdHandler {
    private const val TAG = "GlobalCustomCmdHandler"

    // thread-safe set for observers
    private val observers: MutableSet<(String) -> Unit> = CopyOnWriteArraySet()

    private val listener = CustomCmdListener { name, caps ->
        try {
            Log.i(TAG, "CustomCmdListener invoked. name=$name, caps=$caps") // 添加这行调试日志
            if (name != CONSTANT.CUSTOM_CMD) {
                Log.w(TAG, "Ignored: name mismatch. Expected=${CONSTANT.CUSTOM_CMD}, Actual=$name")
                return@CustomCmdListener
            }
            if (caps == null) {
                Log.w(TAG, "Ignored: caps is null")
                return@CustomCmdListener
            }

            // --- DEBUG: Iterate ALL caps content to see exactly what we got ---
            try {
                if (caps != null) {
                   Log.i(TAG, "DUMP CAPS size=${caps.size()}")
                   for(i in 0 until caps.size()) {
                       val v = caps.at(i)
                       Log.i(TAG, "Caps idx=$i type=${v?.type()} string='${v?.string}' int=${v?.int}")
                   }
                }
            } catch(e: Exception) {
               Log.w(TAG, "Dump failed: $e")
            }
            // -----------------------------------------------------------------

            // Build a human-friendly string and notify observers first
            val parsed = capsToString(caps)
            if (parsed.isNotEmpty()) {
                for (obs in observers) {
                    try {
                        obs(parsed)
                    } catch (t: Throwable) {
                        Log.w(TAG, "observer callback failed: ${t.message}")
                    }
                }
            }

            if (capsContainsTakePicture(caps)) {
                Log.i(TAG, "detected take_picture -> attempting to trigger PictureController")

                // Remove concurrent direct call to avoid conflict with UI logic
                // Delegate completely to PictureController

                // Notify PictureController
                try {
                    // Call the standard entry point
                    PictureController.triggerRemoteTake()
                } catch (t: Throwable) {
                    Log.w(TAG, "triggerRemoteTake failed: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "custom cmd handler error: ${t.message}")
        }
    }

    fun register() {
        try {
            CxrApi.getInstance().setCustomCmdListener(listener)
            Log.i(TAG, "registered global customCmd listener. Target Listener Hash=${System.identityHashCode(listener)}")

            // Reflection check: verify if the listener is actually set in CxrApi
            try {
                // Assuming the field name inside CxrApi implementation is 'mCustomCmdListener' or similar.
                // Since we don't know the exact obfuscated name if it exists, we try the most common one seen in logs.
                // The log "send to mCustomCmdListener" suggests the field name might be "mCustomCmdListener".
                var found = false
                var clazz: Class<*>? = CxrApi.getInstance().javaClass
                // Search in class hierarchy too
                while(clazz != null && clazz != Object::class.java) {
                    val fields = clazz.declaredFields
                    for (field in fields) {
                        if (com.rokid.cxr.client.extend.listeners.CustomCmdListener::class.java.isAssignableFrom(field.type)) {
                            field.isAccessible = true
                            val current = field.get(CxrApi.getInstance())
                            Log.i(TAG, "Reflect check: Found field '${field.name}' in ${clazz.simpleName} holding object: ${current} (Hash=${System.identityHashCode(current)})")
                            found = true
                            if (current !== listener) {
                                Log.e(TAG, "CRITICAL WARNING: The listener in CxrApi is NOT GlobalCustomCmdHandler's listener! It has been overwritten or not set correctly!")
                                // Attempt to force set via reflection if normal set failed? (Dangerous, skip for now, just diagnose)
                            } else {
                                Log.i(TAG, "Reflect check: SUCCESS. CxrApi is holding the correct listener.")
                            }
                        }
                    }
                    if(found) break
                    clazz = clazz.superclass
                }
                if (!found) {
                   Log.w(TAG, "Reflection check: Could not find any field of type CustomCmdListener in CxrApi hierarchy.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reflection check failed: $e")
            }

        } catch (t: Throwable) {
            Log.w(TAG, "register failed: ${t.message}")
        }
    }

    fun unregister() {
        try {
            CxrApi.getInstance().setCustomCmdListener(null)
            Log.i(TAG, "unregistered global customCmd listener")
        } catch (t: Throwable) {
            Log.w(TAG, "unregister failed: ${t.message}")
        }
    }

    fun addObserver(cb: (String) -> Unit) {
        observers.add(cb)
    }

    fun removeObserver(cb: (String) -> Unit) {
        observers.remove(cb)
    }

    private fun capsContainsTakePicture(caps: Caps): Boolean {
        var i = 0
        while (i < caps.size()) {
            val v = caps.at(i)
            when (v.type()) {
                Caps.Value.TYPE_STRING -> {
                    val s = v.string ?: ""
                    if (s.equals("take_picture", ignoreCase = true)
                        || s.equals("takepic", ignoreCase = true)
                        || s.equals("take_pic", ignoreCase = true)
                        || s.equals("take pic", ignoreCase = true)
                    ) return true
                }
                Caps.Value.TYPE_OBJECT -> {
                    val obj = v.`object`
                    if (obj != null && capsContainsTakePicture(obj)) return true
                }
                else -> {
                }
            }
            i++
        }

        // key/value pairs
        i = 0
        while (i + 1 < caps.size()) {
            val key = caps.at(i)
            val value = caps.at(i + 1)
            if (key.type() == Caps.Value.TYPE_STRING && (key.string ?: "").equals("cmd", ignoreCase = true)) {
                if (value.type() == Caps.Value.TYPE_STRING) {
                    val vs = value.string ?: ""
                    if (vs.equals("take_picture", ignoreCase = true)) return true
                }
            }
            i += 2
        }

        return false
    }

    private fun capsToString(caps: Caps): String {
        val sb = StringBuilder()
        var i = 0
        while (i < caps.size()) {
            val v = caps.at(i)
            when (v.type()) {
                Caps.Value.TYPE_STRING -> sb.append(v.string)
                Caps.Value.TYPE_FLOAT -> sb.append(v.float)
                Caps.Value.TYPE_DOUBLE -> sb.append(v.double)
                Caps.Value.TYPE_INT32, Caps.Value.TYPE_UINT32 -> sb.append(v.int)
                Caps.Value.TYPE_OBJECT -> sb.append(capsToString(v.`object`))
                Caps.Value.TYPE_BINARY -> sb.append("<binary>")
                else -> sb.append("<unknown>")
            }
            sb.append(',')
            i++
        }
        if (sb.endsWith(",")) sb.setLength(sb.length - 1)
        return sb.toString()
    }
}
