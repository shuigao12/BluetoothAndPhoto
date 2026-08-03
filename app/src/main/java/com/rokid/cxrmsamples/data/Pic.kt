package com.rokid.cxrmsamples.data

data class Pic(
    val pathSeg: String,
    val pathFull: String?,
    val percent: Float,
    var name: String,
    var date: String,
    val createdAt: Long = System.currentTimeMillis(),
    val timingInfo: String? = null,
    val modelName: String? = null
)

fun String.toEnglishTimingInfo(): String = split(';')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filterNot {
        it.startsWith("Stage3") || it.startsWith("Stage 3", ignoreCase = true)
    }
    .joinToString("; ")
    .replace("模型推理(总)", "Inference total")
    .replace("Stage1粗分割", "Stage 1 coarse segmentation")
    .replace("Stage2精细分割", "Stage 2 fine segmentation")
    .replace("解码图片", "Decode image")
    .replace("缩放图片", "Resize image")
    .replace("生成名称", "Generate name")
    .replace("自动保存", "Auto-save")
    .replace("绘制叠加", "Render overlay")
    .replace("推理总计", "Inference total")
    .replace("总计", "Total")
