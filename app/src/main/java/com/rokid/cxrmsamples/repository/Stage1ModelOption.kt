package com.rokid.cxrmsamples.repository

enum class ModelRuntime {
    ONNX,
    PTL
}

data class Stage1ModelOption(
    val id: String,
    val assetFileName: String,
    val displayName: String,
    val subtitle: String,
    val runtime: ModelRuntime,
    val useXnnpack: Boolean = false,
    val useBasicGraphOpt: Boolean = false
)

object Stage1ModelCatalog {
    val allOptions: List<Stage1ModelOption> = listOf(
        Stage1ModelOption(
            id = "coarse_seg2class_ptl",
            assetFileName = "coarse_seg2class.ptl",
            displayName = "coarse_seg2class PTL",
            subtitle = "Coarse segmentation · PyTorch Lite",
            runtime = ModelRuntime.PTL
        ),
        Stage1ModelOption(
            id = "coarse_seg2class_xnnpack_int8",
            assetFileName = "coarse_seg2class_xnnpack_int8.onnx",
            displayName = "coarse_seg2class XNNPACK INT8",
            subtitle = "Coarse segmentation · XNNPACK INT8",
            runtime = ModelRuntime.ONNX,
            useXnnpack = true,
            useBasicGraphOpt = true
        ),
        Stage1ModelOption(
            id = "coarse_seg2class_fp32",
            assetFileName = "coarse_seg2class.onnx",
            displayName = "coarse_seg2class FP32",
            subtitle = "Coarse segmentation · ONNX FP32",
            runtime = ModelRuntime.ONNX
        )
    )

    fun findById(id: String): Stage1ModelOption? = allOptions.firstOrNull { it.id == id }

    val defaultId: String = "coarse_seg2class_xnnpack_int8"
}
