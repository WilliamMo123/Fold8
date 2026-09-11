package com.wmo.foldwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Shows a live, scrubbable preview of the fold transition inside the
 * settings screen — the same shader the wallpaper itself uses, so what you
 * see here while dragging the slider is exactly what you'll get once the
 * hinge angle drives it for real. Mirrors the "pick photos, preview the
 * effect" flow from Apple's iPhone Duo settings.
 */
class TransitionPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var outerBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    var innerBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    /** 0f = fully closed (outer), 1f = fully open (inner). */
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val runtimeShader = RuntimeShader(FoldWallpaperService.AGSL_SOURCE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outer = outerBitmap
        val inner = innerBitmap
        if (outer == null || inner == null || width == 0 || height == 0) {
            canvas.drawColor(Color.parseColor("#22000000"))
            return
        }

        val outerScaled = Bitmap.createScaledBitmap(outer, width, height, true)
        val innerScaled = Bitmap.createScaledBitmap(inner, width, height, true)

        runtimeShader.setInputShader(
            "outerTex",
            BitmapShader(outerScaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        runtimeShader.setInputShader(
            "innerTex",
            BitmapShader(innerScaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        runtimeShader.setFloatUniform("progress", progress)
        runtimeShader.setFloatUniform("resolution", width.toFloat(), height.toFloat())

        paint.shader = runtimeShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
