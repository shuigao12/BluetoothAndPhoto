package com.rokid.cxrmsamples.comm

import android.util.Log
import com.rokid.cxr.Caps
import java.util.Locale

/** 向眼镜发送 UI / 结果通知（CUSTOM_CMD 通道） */
object GlassesNotify {
    private const val TAG = "GlassesNotify"

    fun photoCaptureStart() {
        send(VoiceCmdProtocol.PHOTO_CAPTURE_START)
    }

    fun photoCaptureEnd() {
        send(VoiceCmdProtocol.PHOTO_CAPTURE_END)
    }

    fun segmentationResult(percent: Float) {
        val percentText = String.format(Locale.US, "%.6f", percent)
        val payload = Caps().apply {
            write("percent")
            write(percentText)
        }
        val envelope = Caps().apply {
            write(VoiceCmdProtocol.SEGMENTATION_RESULT)
            write(payload)
        }

        // 主路径：cmd=segmentation_result，与历史可用日志一致
        if (CxrCustomCmdSender.sendCaps(VoiceCmdProtocol.SEGMENTATION_RESULT, envelope)) {
            Log.i(TAG, "segmentation_result sent: percent=$percent")
            return
        }

        // 备用：仅发送 percent 载荷
        if (CxrCustomCmdSender.sendCaps(VoiceCmdProtocol.SEGMENTATION_RESULT, payload)) {
            Log.i(TAG, "segmentation_result sent (flat payload): percent=$percent")
            return
        }

        Log.w(TAG, "segmentation_result send failed: percent=$percent")
    }

    private fun send(payload: String) {
        if (CxrCustomCmdSender.send(payload)) {
            Log.i(TAG, "sent $payload")
        } else {
            Log.w(TAG, "send failed: $payload")
        }
    }
}
