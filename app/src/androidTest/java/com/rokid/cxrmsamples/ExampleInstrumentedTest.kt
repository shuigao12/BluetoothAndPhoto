package com.rokid.cxrmsamples

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.pytorch.LiteModuleLoader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rokid.cxrmsamples", appContext.packageName)
    }

    @Test
    fun testLoadPyTorchModel() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val modelPath = assetFilePath(appContext, "model/coarse_seg2class.ptl")
        assertNotNull(modelPath)
        val module = LiteModuleLoader.load(modelPath)
        assertNotNull(module)
    }

    @Throws(IOException::class)
    private fun assetFilePath(context: android.content.Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
        }
        return file.absolutePath
    }
}