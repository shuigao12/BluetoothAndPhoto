package com.rokid.cxrmsamples.activities.customProtocol

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.CustomCmdListener
import com.rokid.cxrmsamples.dataBeans.CONSTANT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomProtocolViewModel : ViewModel() {

    private val _messageReceived : MutableStateFlow<String?> = MutableStateFlow(null)
    val messageReceived = _messageReceived.asStateFlow()

    private var counter = 0



    private val customCmdListener = CustomCmdListener { p0, p1 ->
        if (p0 == CONSTANT.CUSTOM_CMD){
            p1?.let {
                _messageReceived.value = parseCMD(it)
            }
        }
    }

    fun setCustomCmdListener(toSet: Boolean){
        if (toSet) {
            CxrApi.getInstance().setCustomCmdListener(customCmdListener)
        } else {
            CxrApi.getInstance().setCustomCmdListener(null)
        }
    }

    fun sendCustomMessage(){
        val caps = Caps().apply {
            write("Custom String Message:")
            writeInt32(++ counter)
            write(true)

            write(Caps().apply {
                write("Nested String Message")
            })
        }

        CxrApi.getInstance().sendCustomCmd("Custom Message", caps)
    }

    private fun parseCMD(caps: Caps): String {
        val size = caps.size()
        val sb = StringBuilder()
        for (i in 0 until size) {
            val value = caps.at(i)
            sb.append("value: {")
            sb.append(
                when (value.type()) {
                    Caps.Value.TYPE_STRING -> {
                        Log.d("CustomCMDActivity", "String value: ${value.string}")
                        value.string
                    }
                    Caps.Value.TYPE_INT32 -> {
                        Log.d("CustomCMDActivity", "Int32 value: ${value.int}")
                        value.int
                    }
                    Caps.Value.TYPE_FLOAT -> {
                        Log.d("CustomCMDActivity", "Float value: ${value.float}")
                        value.float
                    }
                    Caps.Value.TYPE_BINARY -> {
                        Log.d("CustomCMDActivity", "Binary value: ${Base64.encodeToString(value.binary.data, Base64.DEFAULT)}")
                        Base64.encodeToString(value.binary.data, Base64.DEFAULT)
                    }
                    Caps.Value.TYPE_DOUBLE -> {
                        Log.d("CustomCMDActivity", "Double value: ${value.double}")
                        value.double
                    }
                    Caps.Value.TYPE_UINT32 -> {
                        Log.d("CustomCMDActivity", "UInt32 value: ${value.int}")
                        value.int
                    }
                    Caps.Value.TYPE_OBJECT -> {
                        Log.d("CustomCMDActivity", "Object value: ${value.`object`}")
                        parseCMD(value.`object`)
                    }
                    else -> {
                        "不是需要的类型"
                    }
                }
            )
            sb.append("},")
        }

        return sb.toString()
    }

}