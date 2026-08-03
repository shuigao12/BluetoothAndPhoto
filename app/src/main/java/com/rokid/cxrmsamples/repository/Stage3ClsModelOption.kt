package com.rokid.cxrmsamples.repository

data class Stage3ClsModelOption(
    val id: String,
    val assetFileName: String,
    val displayName: String,
    val subtitle: String,
    val runtime: ModelRuntime = ModelRuntime.ONNX,
    val useXnnpack: Boolean = false,
    val useBasicGraphOpt: Boolean = false
)

object Stage3ClsModelCatalog {
    val allOptions: List<Stage3ClsModelOption> = listOf(
        Stage3ClsModelOption(
            id = "cls4class_ptl",
            assetFileName = "cls4class.ptl",
            displayName = "cls4class PTL",
            subtitle = "Disease classification · PyTorch Lite",
            runtime = ModelRuntime.PTL
        ),
        Stage3ClsModelOption(
            id = "cls4class_fp32",
            assetFileName = "cls4class.onnx",
            displayName = "cls4class FP32",
            subtitle = "Disease classification · ONNX FP32",
            runtime = ModelRuntime.ONNX
        ),
        Stage3ClsModelOption(
            id = "cls4class_int8",
            assetFileName = "cls4class_xnnpack_int8.onnx",
            displayName = "cls4class XNNPACK INT8",
            subtitle = "Disease classification · XNNPACK INT8",
            runtime = ModelRuntime.ONNX,
            useXnnpack = true,
            useBasicGraphOpt = true
        )
    )

    fun findById(id: String): Stage3ClsModelOption? = allOptions.firstOrNull { it.id == id }

    val defaultId: String = "cls4class_int8"
}
