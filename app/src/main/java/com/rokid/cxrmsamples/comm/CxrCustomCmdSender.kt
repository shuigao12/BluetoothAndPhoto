package com.rokid.cxrmsamples.comm

import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.utils.ValueUtil

object CxrCustomCmdSender {
    private const val TAG = "CxrCustomCmdSender"

    fun send(vararg payloads: String): Boolean {
        if (payloads.isEmpty()) return false
        val cmdName = payloads[0]
        val caps = Caps().apply { payloads.forEach { write(it) } }
        return sendWithCmdName(cmdName, caps)
    }

    fun sendCaps(cmdName: String, caps: Caps): Boolean = sendWithCmdName(cmdName, caps)

    /**
     * 手机→眼镜：与历史可用日志一致，cmd 名用具体指令（如 photo_capture_start），
     * 而不是 CUSTOM_CMD 包一层。
     */
    private fun sendWithCmdName(cmdName: String, caps: Caps): Boolean {
        if (trySend(cmdName, caps)) return true

        val wrapped = Caps().apply {
            write(cmdName)
            write(caps)
        }
        if (trySend(VoiceCmdProtocol.CUSTOM_CMD, wrapped)) return true

        Log.w(TAG, "all send paths failed for cmd=$cmdName")
        return false
    }

    private fun trySend(cmdName: String, caps: Caps): Boolean = trySendString(cmdName, caps)

    private fun trySendString(cmdName: String, caps: Caps): Boolean {
        return try {
            val status = CxrApi.getInstance().sendCustomCmd(cmdName, caps)
            val ok = status == ValueUtil.CxrStatus.REQUEST_SUCCEED
            if (ok) {
                Log.i(TAG, "sendCustomCmd ok cmd=$cmdName capsSize=${caps.size()}")
            } else {
                Log.w(TAG, "sendCustomCmd failed cmd=$cmdName status=$status")
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "sendCustomCmd(String) cmd=$cmdName error: ${t.message}")
            false
        }
    }
}
