package com.rokid.cxrmsamples.activities.usageSelection

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.rokid.cxrmsamples.activities.customView.CustomViewActivity
import com.rokid.cxrmsamples.activities.deviceInformation.DeviceInformationActivity
import com.rokid.cxrmsamples.activities.picture.PictureActivity
import com.rokid.cxrmsamples.dataBeans.UsageType

class UsageSelectionViewModel: ViewModel() {
    fun toUsage(context: Context, type: UsageType) {
        when(type){
            UsageType.USAGE_TYPE_PHOTO -> {
                context.startActivity(Intent(context, PictureActivity::class.java))
                (context as? Activity)?.finish()
            }
            UsageType.USAGE_CUSTOM_VIEW -> {
                context.startActivity(Intent(context, CustomViewActivity::class.java))
            }
            UsageType.USAGE_TYPE_DEVICE_INFORMATION -> {
                context.startActivity(Intent(context, DeviceInformationActivity::class.java))
            }
            else -> {
                // 兜底分支：暂不处理其他用法（例如已移除的自定义协议等），避免 when 不可穷尽导致编译错误
                // 可根据实际需要提示用户或记录日志
            }
        }
    }

}