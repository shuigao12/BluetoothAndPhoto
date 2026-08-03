package com.rokid.cxrmsamples.comm

import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.utils.ValueUtil

/**
 * Simple global controller to allow remote triggers (from custom protocol handlers)
 * to invoke the app's picture-taking logic in `PictureViewModel`.
 *
 * If no ViewModel is registered, it falls back to directly asking the glasses
 * to take a photo via CxrApi.takeGlassPhotoGlobal with a safe default size.
 */
object PictureController {
    private const val TAG = "PictureController"

    // callback set by PictureViewModel; invoked on remote command
    @Volatile
    var onRemoteTake: (() -> Unit)? = null

    fun triggerRemoteTake() {
        try {
            val cb = onRemoteTake
            if (cb != null) {
                // UI page is registered: call its logic directly (same as button click)
                Log.i(TAG, "onRemoteTake callback found, invoking (Logic same as Button Click)")
                try {
                    cb.invoke()
                } catch (t: Throwable) {
                    Log.w(TAG, "onRemoteTake invocation threw: ${t.message}")
                }
            } else {
                Log.i(TAG, "no onRemoteTake registered, using fallback takeGlassPhotoGlobal")
                try {
                    val ret = CxrApi.getInstance().takeGlassPhotoGlobal(1280, 720, 100, PhotoResultCallback { status, imageData ->
                        Log.i(TAG, "fallback PhotoResultCallback invoked: status=$status, imageData.size=${imageData?.size ?: 0}")
                        if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED) {
                            Log.i(TAG, "fallback photo succeeded")
                        }
                    })
                    Log.i(TAG, "PictureController.fallback takeGlassPhotoGlobal returned: $ret")
                } catch (t: Throwable) {
                    Log.w(TAG, "fallback takeGlassPhotoGlobal failed: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "triggerRemoteTake failed: ${t.message}")
        }
    }
}
