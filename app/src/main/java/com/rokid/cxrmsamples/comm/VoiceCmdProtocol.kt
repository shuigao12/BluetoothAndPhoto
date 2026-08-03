package com.rokid.cxrmsamples.comm

import com.rokid.cxrmsamples.dataBeans.CONSTANT

/** 与眼镜端 [VoiceCmdProtocol] 对齐的 CUSTOM_CMD 字段 */
object VoiceCmdProtocol {
    const val CUSTOM_CMD = CONSTANT.CUSTOM_CMD

    const val VOICE_MODE_ON = "voice_mode_on"
    const val VOICE_MODE_OFF = "voice_mode_off"
    const val AI_WAKE = "ai_wake"
    const val TAKE_PICTURE = "take_picture"

    const val PHOTO_CAPTURE_START = "photo_capture_start"
    const val PHOTO_CAPTURE_END = "photo_capture_end"
    const val SEGMENTATION_RESULT = "segmentation_result"
}
