package com.rokid.cxrmsamples.activities.model

object DiseaseLevel {
    private val LABELS = listOf("level1", "level2", "level3", "level4")

    fun fromIndex(index: Int): String = LABELS.getOrElse(index.coerceIn(0, LABELS.lastIndex)) { "level1" }

    fun argmax(logits: FloatArray): String {
        if (logits.isEmpty()) return "level1"
        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return fromIndex(maxIdx)
    }
}
