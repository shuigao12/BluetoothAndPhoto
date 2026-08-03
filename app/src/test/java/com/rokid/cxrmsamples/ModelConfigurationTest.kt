package com.rokid.cxrmsamples

import com.rokid.cxrmsamples.data.toEnglishTimingInfo
import com.rokid.cxrmsamples.repository.Stage1ModelCatalog
import com.rokid.cxrmsamples.repository.Stage2SegModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigurationTest {
    @Test
    fun defaultPipelineUsesXnnpackInt8Models() {
        val stage1 = requireNotNull(Stage1ModelCatalog.findById(Stage1ModelCatalog.defaultId))
        val stage2 = requireNotNull(Stage2SegModelCatalog.findById(Stage2SegModelCatalog.defaultId))
        val defaults = listOf(
            stage1.useXnnpack to stage1.assetFileName,
            stage2.useXnnpack to stage2.assetFileName
        )

        defaults.forEach { (usesXnnpack, assetFileName) ->
            assertTrue(usesXnnpack)
            assertTrue(assetFileName.endsWith("_xnnpack_int8.onnx"))
        }
    }

    @Test
    fun legacyTimingInformationIsPresentedInEnglish() {
        val legacy = "模型推理(总)=5038ms; Stage1粗分割=2065ms; Stage2精细分割=1674ms; Stage3病害分级=226ms; 生成名称=17ms; 推理总计=5036ms"

        assertEquals(
            "Inference total=5038ms; Stage 1 coarse segmentation=2065ms; Stage 2 fine segmentation=1674ms; Generate name=17ms; Inference total=5036ms",
            legacy.toEnglishTimingInfo()
        )
    }
}
