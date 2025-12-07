package com.rokid.cxrmsamples.activities.customView

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.infos.IconInfo
import com.rokid.cxr.client.extend.listeners.CustomViewListener
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.dataBeans.selfView.ImageViewProps
import com.rokid.cxrmsamples.dataBeans.selfView.LinearLayoutProps
import com.rokid.cxrmsamples.dataBeans.selfView.SelfViewJson
import com.rokid.cxrmsamples.dataBeans.selfView.TextViewProps
import com.rokid.cxrmsamples.dataBeans.selfView.UpdateViewJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class CustomViewViewModel: ViewModel() {

    private val TAG = "CustomViewViewModel"

    private val _isCustomViewRunning = MutableStateFlow(false)
    val isCustomViewRunning = _isCustomViewRunning.asStateFlow()

    private val _iconSent = MutableStateFlow(false)
    val iconSent = _iconSent.asStateFlow()

    private val customViewListener = object : CustomViewListener {
        override fun onIconsSent() {
            _iconSent.value = true
        }

        override fun onOpened() {
            _isCustomViewRunning.value = true
        }

        override fun onOpenFailed(p0: Int) {
            _isCustomViewRunning.value = false
        }

        override fun onUpdated() {
        }

        override fun onClosed() {
            _isCustomViewRunning.value = false
        }
    }

    private val selfView = SelfViewJson().apply {
        type = "LinearLayout"
        props = LinearLayoutProps().apply {
            id = "root"
            layout_width = "match_parent"
            layout_height = "match_parent"
            marginTop = "160dp"
            marginBottom = "80dp"
            backgroundColor = "#FF000000"
            orientation = "vertical"
            gravity="center_horizontal"
        }.toJson()
        children = listOf(
            SelfViewJson().apply {
                type = "TextView"
                props = TextViewProps().apply {
                    id = "textView"
                    layout_width = "wrap_content"
                    layout_height = "wrap_content"
                    text = "Hello World"
                    textColor = "#00FF00"
                    textSize = "16sp"
                    gravity = "center"
                    textStyle = "bold"
                    paddingStart = "16dp"
                    paddingEnd = "16dp"
                }.toJson()
            },
            SelfViewJson().apply {
                type = "ImageView"
                props = ImageViewProps().apply {
                    id = "imageView"
                    layout_width = "120dp"
                    layout_height = "120dp"
                    name = "vector"
                    scaleType = "center"
                }.toJson()
            }
        )
    }

    fun setCustomSceneListener(toSet: Boolean) {
        if (toSet){
            CxrApi.getInstance().setCustomViewListener(customViewListener)
        }else{
            CxrApi.getInstance().setCustomViewListener(null)
        }
    }

    fun toggleCustomView() {
        if (!_isCustomViewRunning.value){
            // 开启自定义View
            CxrApi.getInstance().openCustomView(selfView.toJson())
        }else{
            // 关闭自定义View
            CxrApi.getInstance().closeCustomView()
        }
    }
    private var count = 0
    fun updateCustomView() {
        Log.d(TAG, "updateSelfView: 更新自定义视图, 当前计数: $count")
        val updateViewJson = if (count % 2 == 0){
            UpdateViewJson().apply {
                updateList.add(UpdateViewJson.UpdateJson(id = "imageView").apply {
                    props["name"] = "icon1"
                })

            }
        }else{
            UpdateViewJson().apply {
                updateList.add(UpdateViewJson.UpdateJson(id = "imageView").apply {
                    props["name"] = "vector"
                })
            }
        }
        updateViewJson.updateList.add(UpdateViewJson.UpdateJson(id = "textView").apply {
            props["text"] = "Hello Rokid $count"
        })

        selfView.children?.get(0)?.props = TextViewProps().apply {
            id = "textView"
            layout_width = "wrap_content"
            layout_height = "wrap_content"
            text = "Hello Rokid $count"
            textColor = "#00FF00"
            textSize = "16sp"
            gravity = "center"
            textStyle = "bold"
            paddingStart = "16dp"
            paddingEnd = "16dp"
        }.toJson()

        selfView.children?.get(1)?.props = ImageViewProps().apply {
            id = "imageView"
            layout_width = "120dp"
            layout_height = "120dp"
            name = if (count % 2 == 0) "icon1" else "vector"
            scaleType = "center"
        }.toJson()

        count ++
        if (count >= Int.MAX_VALUE){
            count = 1
        }
        Log.d(TAG, "updateSelfView: 发送更新配置")
        CxrApi.getInstance().updateCustomView(updateViewJson.toJson())
        Log.d(TAG, "updateSelfView: 自定义视图已更新")
    }

    /**
     * 上传图标资源
     * 包括icon1.png和vector.png两个图标
     */
    fun uploadIcon(context: Context) {
        try {
            Log.d(TAG, "uploadIcon: 开始上传图标资源")
            // Convert icon1.png to Base64
            val icon1Drawable = context.resources.getDrawable(R.drawable.icon1, null)
            val icon1Bitmap = drawableToBitmap(icon1Drawable)
            val icon1OutputStream = ByteArrayOutputStream()
            icon1Bitmap.compress(Bitmap.CompressFormat.PNG, 100, icon1OutputStream)
            val icon1Base64 = Base64.encodeToString(icon1OutputStream.toByteArray(), Base64.NO_WRAP)
            icon1OutputStream.close()
            Log.d(TAG, "uploadIcon: icon1.png转换完成")

            // Convert vector.png to Base64
            val vectorDrawable = context.resources.getDrawable(R.drawable.vector, null)
            val vectorBitmap = drawableToBitmap(vectorDrawable)
            val vectorOutputStream = ByteArrayOutputStream()
            vectorBitmap.compress(Bitmap.CompressFormat.PNG, 100, vectorOutputStream)
            val vectorBase64 = Base64.encodeToString(vectorOutputStream.toByteArray(), Base64.NO_WRAP)
            vectorOutputStream.close()
            Log.d(TAG, "uploadIcon: vector.png转换完成")

            // Create IconInfo objects
            val icon1Info = IconInfo("icon1", icon1Base64)
            val vectorInfo = IconInfo("vector", vectorBase64)

            // Send icons using CXR API
            CxrApi.getInstance().sendCustomViewIcons(listOf(icon1Info, vectorInfo))
            Log.d("SelfDesignViewActivity", "uploadIcon: 图标发送成功")
            Log.d(TAG, "uploadIcon: 图标上传完成")
        } catch (e: Exception) {
            Log.e("SelfDesignViewActivity", "uploadIcon: 图标发送失败", e)
            Log.e(TAG, "uploadIcon: 图标上传异常", e)
        }
    }

    /**
     * 将Drawable对象转换为Bitmap对象
     * @param drawable 需要转换的Drawable对象
     * @return 转换后的Bitmap对象
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            Log.d(TAG, "drawableToBitmap: Drawable已经是BitmapDrawable类型")
            return drawable.bitmap
        }

        Log.d(TAG, "drawableToBitmap: 将Drawable转换为Bitmap")
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        Log.d(TAG, "drawableToBitmap: Drawable转换完成")
        return bitmap
    }


}