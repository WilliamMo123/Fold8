package com.wmo.foldwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Settings screen (also opened from the system wallpaper picker's
 * "Customize" button, since it's registered as android:settingsActivity).
 * Lets the user pick outer/inner screenshots, scrub a live preview of the
 * transition, reset the choice, and jump to "set as wallpaper" — the same
 * pick-preview-apply flow Apple's own settings use for iPhone Duo.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var outerPreview: ImageView
    private lateinit var innerPreview: ImageView
    private lateinit var previewView: TransitionPreviewView

    private val pickOuter = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveImage(it, WallpaperImageStore.OUTER_FILE, outerPreview, isOuter = true) }
    }

    private val pickInner = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveImage(it, WallpaperImageStore.INNER_FILE, innerPreview, isOuter = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        outerPreview = findViewById(R.id.outerPreview)
        innerPreview = findViewById(R.id.innerPreview)
        previewView = findViewById(R.id.previewView)

        // Restore previously picked images, if any.
        WallpaperImageStore.loadBitmap(this, WallpaperImageStore.OUTER_FILE)?.let {
            outerPreview.setImageBitmap(it)
            previewView.outerBitmap = it
        }
        WallpaperImageStore.loadBitmap(this, WallpaperImageStore.INNER_FILE)?.let {
            innerPreview.setImageBitmap(it)
            previewView.innerBitmap = it
        }

        findViewById<Button>(R.id.pickOuterBtn).setOnClickListener { pickOuter.launch("image/*") }
        findViewById<Button>(R.id.pickInnerBtn).setOnClickListener { pickInner.launch("image/*") }

        findViewById<SeekBar>(R.id.previewSeekBar).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    previewView.progress = value / 100f
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        findViewById<Button>(R.id.resetBtn).setOnClickListener {
            File(filesDir, WallpaperImageStore.OUTER_FILE).delete()
            File(filesDir, WallpaperImageStore.INNER_FILE).delete()
            outerPreview.setImageBitmap(null)
            innerPreview.setImageBitmap(null)
            previewView.outerBitmap = null
            previewView.innerBitmap = null
            WallpaperImageStore.notifyChanged(this)
            Toast.makeText(this, "已清除,請重新選擇兩張圖片", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.setWallpaperBtn).setOnClickListener {
            if (!WallpaperImageStore.hasBothImages(this)) {
                Toast.makeText(this, "請先選擇兩張圖片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openLiveWallpaperPicker()
        }
    }

    private fun saveImage(uri: Uri, targetFileName: String, preview: ImageView, isOuter: Boolean) {
        contentResolver.openInputStream(uri)?.use { input ->
            val file = File(filesDir, targetFileName)
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val bitmap = BitmapFactory.decodeFile(File(filesDir, targetFileName).absolutePath)
        preview.setImageBitmap(bitmap)
        if (isOuter) previewView.outerBitmap = bitmap else previewView.innerBitmap = bitmap
        // Ask any running wallpaper engine to reload the new images.
        WallpaperImageStore.notifyChanged(this)
    }

    private fun openLiveWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        intent.putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, FoldWallpaperService::class.java)
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "無法開啟桌布選擇畫面: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
