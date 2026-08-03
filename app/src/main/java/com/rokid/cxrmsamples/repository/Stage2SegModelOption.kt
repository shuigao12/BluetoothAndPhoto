package com.rokid.cxrmsamples.repository

data class Stage2SegModelOption(
    val id: String,
    val assetFileName: String,
    val displayName: String,
    val subtitle: String,
    val runtime: ModelRuntime = ModelRuntime.ONNX,
    val useXnnpack: Boolean = false,
    val useBasicGraphOpt: Boolean = false
)

object Stage2SegModelCatalog {
    val allOptions: List<Stage2SegModelOption> = listOf(
        Stage2SegModelOption(
            id = "fine_seg3class_ptl",
            assetFileName = "fine_seg3class.ptl",
            displayName = "fine_seg3class PTL",
            subtitle = "Fine segmentation · PyTorch Lite",
            runtime = ModelRuntime.PTL
        ),
        Stage2SegModelOption(
            id = "fine_seg3class_xnnpack_int8",
            assetFileName = "fine_seg3class_xnnpack_int8.onnx",
            displayName = "fine_seg3class XNNPACK INT8",
            subtitle = "Fine segmentation · XNNPACK INT8",
            runtime = ModelRuntime.ONNX,
            useXnnpack = true,
            useBasicGraphOpt = true
        ),
        Stage2SegModelOption(
            id = "fine_seg3class_fp32",
            assetFileName = "fine_seg3class.onnx",
            displayName = "fine_seg3class FP32",
            subtitle = "Fine segmentation · ONNX FP32",
            runtime = ModelRuntime.ONNX
        )
    )

    fun findById(id: String): Stage2SegModelOption? = allOptions.firstOrNull { it.id == id }

    val defaultId: String = "fine_seg3class_xnnpack_int8"
}
