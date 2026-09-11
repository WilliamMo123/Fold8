package com.wmo.foldwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Small helper shared between MainActivity (which saves the two source
 * images) and FoldWallpaperService (which reads them back for rendering).
 */
object WallpaperImageStore {
    const val OUTER_FILE = "outer_screen.png"
    const val INNER_FILE = "inner_screen.png"

    fun hasBothImages(context: Context): Boolean {
        return File(context.filesDir, OUTER_FILE).exists() &&
            File(context.filesDir, INNER_FILE).exists()
    }

    fun loadBitmap(context: Context, fileName: String): Bitmap? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    // Bumped whenever a new image is saved; the wallpaper engine polls this
    // value each draw cycle and reloads bitmaps when it changes.
    @Volatile
    var version: Long = 0
        private set

    fun notifyChanged(context: Context) {
        version = System.currentTimeMillis()
    }
}
