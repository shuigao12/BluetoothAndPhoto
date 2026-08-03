package com.rokid.cxrmsamples.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PicDBHelper private constructor(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
              $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
              $COL_PATH_SEG TEXT NOT NULL,
              $COL_PATH_FULL TEXT,
              $COL_PERCENT REAL NOT NULL,
              $COL_NAME TEXT NOT NULL,
              $COL_DATE TEXT NOT NULL,
              $COL_CREATED_AT INTEGER,
              $COL_TIMING_INFO TEXT,
              $COL_MODEL_NAME TEXT,
              $COL_DISEASE_LEVEL TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pics_name ON $TABLE($COL_NAME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_TIMING_INFO TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_MODEL_NAME TEXT")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_DISEASE_LEVEL TEXT")
        }
    }

    fun openWriteLink(): SQLiteDatabase = writableDatabase
    fun openReadLink(): SQLiteDatabase = readableDatabase
    fun closeLink() = close()

    fun insert(pic: Pic): Long {
        val cv = ContentValues().apply {
            put(COL_PATH_SEG, pic.pathSeg)
            put(COL_PATH_FULL, pic.pathFull)
            put(COL_PERCENT, pic.percent)
            put(COL_NAME, pic.name)
            put(COL_DATE, pic.date)
            put(COL_CREATED_AT, pic.createdAt)
            put(COL_TIMING_INFO, pic.timingInfo)
            put(COL_MODEL_NAME, pic.modelName)
            put(COL_DISEASE_LEVEL, pic.diseaseLevel)
        }
        return writableDatabase.insert(TABLE, null, cv)
    }

    fun update(nameOld: String, nameNew: String, date: String): Int {
        val cv = ContentValues().apply {
            put(COL_NAME, nameNew)
            put(COL_DATE, date)
        }
        return writableDatabase.update(TABLE, cv, "$COL_NAME=?", arrayOf(nameOld))
    }

    fun delete(name: String): Int {
        return writableDatabase.delete(TABLE, "$COL_NAME=?", arrayOf(name))
    }

    fun deleteAll(): Int {
        return writableDatabase.delete(TABLE, null, null)
    }

    fun queryAll(): List<Pic> {
        val list = mutableListOf<Pic>()
        val c: Cursor = readableDatabase.query(
            TABLE,
            arrayOf(
                COL_PATH_SEG, COL_PATH_FULL, COL_PERCENT, COL_NAME, COL_DATE,
                COL_CREATED_AT, COL_TIMING_INFO, COL_MODEL_NAME, COL_DISEASE_LEVEL
            ),
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        c.use {
            while (it.moveToNext()) {
                list.add(readPicFromCursor(it))
            }
        }
        return list
    }

    fun queryByName(name: String): Pic? {
        val db = readableDatabase
        var pic: Pic? = null
        var cursor: Cursor? = null
        try {
            cursor = db.query(TABLE, null, "$COL_NAME = ?", arrayOf(name), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                pic = readPicFromCursor(cursor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return pic
    }

    private fun readPicFromCursor(cursor: Cursor): Pic {
        val pathSeg = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH_SEG))
        val pathFull = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH_FULL))
        val percent = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_PERCENT))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
        val date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE))
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        val timingIdx = cursor.getColumnIndex(COL_TIMING_INFO)
        val timingInfo = if (timingIdx >= 0 && !cursor.isNull(timingIdx)) {
            cursor.getString(timingIdx)
        } else {
            null
        }
        val modelIdx = cursor.getColumnIndex(COL_MODEL_NAME)
        val modelName = if (modelIdx >= 0 && !cursor.isNull(modelIdx)) {
            cursor.getString(modelIdx)
        } else {
            null
        }
        val levelIdx = cursor.getColumnIndex(COL_DISEASE_LEVEL)
        val diseaseLevel = if (levelIdx >= 0 && !cursor.isNull(levelIdx)) {
            cursor.getString(levelIdx)
        } else {
            null
        }
        return Pic(pathSeg, pathFull, percent, name, date, createdAt, timingInfo, modelName, diseaseLevel)
    }

    companion object {
        private const val DB_NAME = "pics.db"
        private const val DB_VERSION = 4
        private const val TABLE = "pics"
        private const val COL_ID = "id"
        private const val COL_PATH_SEG = "path_seg"
        private const val COL_PATH_FULL = "path_full"
        private const val COL_PERCENT = "percent"
        private const val COL_NAME = "name"
        private const val COL_DATE = "date"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_TIMING_INFO = "timing_info"
        private const val COL_MODEL_NAME = "model_name"
        private const val COL_DISEASE_LEVEL = "disease_level"

        @Volatile private var instance: PicDBHelper? = null
        fun getInstance(ctx: Context): PicDBHelper = instance ?: synchronized(this) {
            instance ?: PicDBHelper(ctx.applicationContext).also { instance = it }
        }
    }
}
