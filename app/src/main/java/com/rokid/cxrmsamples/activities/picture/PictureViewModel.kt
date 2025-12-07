package com.rokid.cxrmsamples.activities.picture

import android.graphics.BitmapFactory
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel class for handling picture capturing functionality.
 * Manages the state of picture taking, image display, and camera settings.
 */
class PictureViewModel : ViewModel() {
    private val TAG = "PictureViewModel"

    /**
     * Array of available picture sizes for capturing images.
     * The camera on the Glasses has been rotated by 90°, so in this context,
     * the [Size.getWidth] from [androidx.compose.ui.geometry.Size] represents the actual image's height,
     * while [Size.getHeight] represents the actual image's width.
     */
    val pictureSize: Array<Size> = arrayOf(
        Size(1920, 1080),
        Size(4032, 3024),
        Size(4000, 3000),
        Size(4032, 2268),
        Size(3264, 2448),
        Size(3200, 2400),
        Size(2268, 3024),
        Size(2876, 2156),
        Size(2688, 2016),
        Size(2582, 1936),
        Size(2400, 1800),
        Size(1800, 2400),
        Size(2560, 1440),
        Size(2400, 1350),
        Size(2048, 1536),
        Size(2016, 1512),
        Size(1600, 1200),
        Size(1440, 1080),
        Size(1280, 720),
        Size(720, 1280),
        Size(1024, 768),
        Size(800, 600),
        Size(648, 648),
        Size(854, 480),
        Size(800, 480),
        Size(640, 480),
        Size(480, 640),
        Size(352, 288),
        Size(320, 240),
        Size(320, 180),
        Size(176, 144)

    )

    /** State flow indicating if a photo is currently being taken */
    private val _takingPhoto = MutableStateFlow(false)
    val takingPhoto = _takingPhoto.asStateFlow()

    /** State flow holding the currently selected picture size */
    private val _selectedPictureSize = MutableStateFlow(pictureSize[0])
    val selectedPictureSize = _selectedPictureSize.asStateFlow()

    /** State flow holding the captured image as ImageBitmap for display */
    private val _showImageBitmap: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    val showImageBitmap = _showImageBitmap.asStateFlow()

    /** Callback for handling the result of photo capture */
    private val pictureCallback = PhotoResultCallback { status, imageData ->
        _takingPhoto.value = false
        when (status) {
            ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                // imageData is a webP format image
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                _showImageBitmap.value = bitmap.asImageBitmap()

            }

            else -> {
                _showImageBitmap.value = null
            }
        }
    }

    /**
     * Capture a picture with the currently selected size.
     * Uses the CXR API to take a photo with the glass camera.
     */
    fun takePicture() {
        _takingPhoto.value = true
        // Get the selected picture size
        val size = _selectedPictureSize.value
        // Take a photo with the selected size, because the camera on the Glasses has been rotated by 90°,
        // so the result image's height is the first param while the result image's width is the second param.
        // the third param is the quality of the image, the value range is [0,100], 100 means the best quality.
        // the fourth param is the callback for handling the result of photo capture.
        when(CxrApi.getInstance().takeGlassPhotoGlobal(size.width, size.height, 100, pictureCallback)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                // The photo capture request has been sent successfully.
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                // The photo capture request has failed.
                _takingPhoto.value = false
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // The photo capture request is waiting for the Glasses to be ready.
            }
            else -> {
                // The photo capture request has failed.
                _takingPhoto.value = false
            }
        }
    }

    /**
     * Select a picture size from the available options.
     * @param resolution The selected picture size
     */
    fun sizeChoose(resolution: Size) {
        _selectedPictureSize.value = resolution
    }

    /**
     * Set the photo parameters for the camera based on selected size.
     * Configures the CXR API with the width and height of the selected picture size.
     */
    fun setPhotoParams() {
        val result = CxrApi.getInstance()
            .setPhotoParams(_selectedPictureSize.value.width, _selectedPictureSize.value.height)
        when(result){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                // The photo parameters have been set successfully.
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                // The photo parameters have failed to be set.
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // The photo parameters are waiting for the Glasses to be ready.
            }
            else -> {
                // The photo parameters have failed to be set.
            }
        }
    }
}