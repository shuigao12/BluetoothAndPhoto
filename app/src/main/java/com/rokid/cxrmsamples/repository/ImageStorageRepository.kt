package com.rokid.cxrmsamples.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rokid.cxrmsamples.data.Pic
import com.rokid.cxrmsamples.data.PicDBHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Repository for managing image storage and database operations.
 */
class ImageStorageRepository(private val context: Context) {
    private val TAG = "ImageStorageRepo"

    /**
     * Saves segmentation result to file storage and database.
     */
    suspend fun saveSegmentationResult(
        bitmap: Bitmap,
        percent: Float,
        name: String,
        date: String,
        timingInfo: String? = null,
        modelName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val existingPic = PicDBHelper.getInstance(context).queryByName(name)
        if (existingPic != null) {
            Log.w(TAG, "Save failed: Name '$name' already exists.")
            return@withContext false
        }

        try {
            val filesDir = context.filesDir
            val outDir = File(filesDir, "segmented")
            if (!outDir.exists()) {
                val created = outDir.mkdirs()
                if (!created && !outDir.exists()) {
                    throw IOException("Failed to create output dir: ${outDir.absolutePath}")
                }
            }
            val filename = "${name}.jpg"
            val outFile = File(outDir, filename)
            FileOutputStream(outFile).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                    throw IOException("Bitmap compress returned false")
                }
                fos.flush()
            }
            val pathSeg = outFile.absolutePath
            val pathFull: String? = null

            val pic = Pic(
                pathSeg = pathSeg,
                pathFull = pathFull,
                percent = percent,
                name = name,
                date = date,
                timingInfo = timingInfo,
                modelName = modelName
            )
            val db = PicDBHelper.getInstance(context)
            try {
                db.openWriteLink()
                val rowId = db.insert(pic)
                Log.i(TAG, "Saved pic rowId=$rowId, path=$pathSeg, model=$modelName, timing=$timingInfo")
                if (rowId == -1L) return@withContext false
            } finally {
                db.closeLink()
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Save result failed: ${e.message}", e)
            return@withContext false
        }
    }
}
