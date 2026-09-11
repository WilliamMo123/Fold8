package com.wmo.foldwallpaper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Live wallpaper that mimics the "iPhone Duo" open/close transition, using
 * the same building blocks reported for the Galaxy Z Fold 8 recreation:
 *   - Sensor.TYPE_HINGE_ANGLE for real-time fold angle
 *   - an AGSL RuntimeShader that interpolates between two source images
 *     based on that angle, plus a moving glass-like highlight band
 *
 * Limitation (same as the original proof-of-concept): this only replaces
 * the *wallpaper layer*. Samsung does not expose System UI-level hooks to
 * third-party apps, so this cannot animate the actual launcher/app
 * transition the way a first-party OS feature could.
 */
class FoldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FoldEngine()

    private inner class FoldEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private var sensorManager: SensorManager? = null
        private var hingeSensor: Sensor? = null

        // 0f = fully closed (show outer screen), 1f = fully open (show inner screen)
        @Volatile private var progress = 0f

        private var outerBitmap: Bitmap? = null
        private var innerBitmap: Bitmap? = null
        private var loadedVersion = -1L

        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private val runtimeShader = RuntimeShader(AGSL_SOURCE)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fallback animation driver used only when no hinge sensor is present
        // (e.g. testing on a non-foldable device), so you can still see the effect.
        private var fallbackDirection = 1f

        private val drawRunnable = object : Runnable {
            override fun run() {
                draw()
                if (visible) {
                    handler.postDelayed(this, FRAME_INTERVAL_MS)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            hingeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                registerSensor()
                handler.post(drawRunnable)
            } else {
                unregisterSensor()
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
        }

        override fun onDestroy() {
            super.onDestroy()
            unregisterSensor()
            handler.removeCallbacks(drawRunnable)
        }

        private fun registerSensor() {
            val sensor = hingeSensor
            if (sensor != null) {
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            // If there's no hinge sensor at all, `progress` will be driven by
            // the fallback ping-pong animation inside draw().
        }

        private fun unregisterSensor() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
                // Reported range is roughly 0f (closed) .. 180f (fully flat/open).
                val angle = event.values.getOrNull(0) ?: return
                progress = (angle / 180f).coerceIn(0f, 1f)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun ensureBitmapsLoaded() {
            if (loadedVersion == WallpaperImageStore.version && outerBitmap != null && innerBitmap != null) return
            outerBitmap = WallpaperImageStore.loadBitmap(applicationContext, WallpaperImageStore.OUTER_FILE)
            innerBitmap = WallpaperImageStore.loadBitmap(applicationContext, WallpaperImageStore.INNER_FILE)
            loadedVersion = WallpaperImageStore.version
        }

        private fun draw() {
            if (surfaceWidth == 0 || surfaceHeight == 0) return
            ensureBitmapsLoaded()

            val outer = outerBitmap
            val inner = innerBitmap
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas ?: return

                if (outer == null || inner == null) {
                    // No source images picked yet: show a neutral background
                    // with a short hint instead of crashing.
                    canvas.drawColor(Color.parseColor("#111318"))
                    return
                }

                if (hingeSensor == null) {
                    // No real hinge sensor on this device: animate progress
                    // back and forth so the effect is still visible.
                    progress += 0.01f * fallbackDirection
                    if (progress >= 1f) { progress = 1f; fallbackDirection = -1f }
                    if (progress <= 0f) { progress = 0f; fallbackDirection = 1f }
                }

                val outerScaled = Bitmap.createScaledBitmap(outer, surfaceWidth, surfaceHeight, true)
                val innerScaled = Bitmap.createScaledBitmap(inner, surfaceWidth, surfaceHeight, true)

                runtimeShader.setInputShader(
                    "outerTex",
                    BitmapShader(outerScaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                )
                runtimeShader.setInputShader(
                    "innerTex",
                    BitmapShader(innerScaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                )
                runtimeShader.setFloatUniform("progress", progress)
                runtimeShader.setFloatUniform("resolution", surfaceWidth.toFloat(), surfaceHeight.toFloat())

                paint.shader = runtimeShader
                canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), paint)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 16L // ~60fps

        // AGSL (Android Graphics Shading Language) source. Blends outerTex
        // and innerTex based on `progress`, and adds a moving translucent
        // "glass" highlight band while the transition is in flight, to
        // approximate the thin-film / light-sweep look of the reference
        // animation.
        const val AGSL_SOURCE = """
            uniform shader outerTex;
            uniform shader innerTex;
            uniform float progress;
            uniform float2 resolution;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;

                half4 outerColor = outerTex.eval(fragCoord);
                half4 innerColor = innerTex.eval(fragCoord);

                // Soft vertical wipe synced to the fold progress.
                float edge = smoothstep(progress - 0.12, progress + 0.12, uv.x);
                half4 blended = mix(outerColor, innerColor, edge);

                // Thin, bright "glass" band sweeping across during the
                // transition, faded out near the two resting states.
                float bandWidth = 0.10;
                float dist = abs(uv.x - progress);
                float band = smoothstep(bandWidth, 0.0, dist);
                float restFade = smoothstep(0.0, 0.15, progress) * smoothstep(1.0, 0.85, progress);
                half4 glass = half4(1.0, 1.0, 1.0, 1.0) * band * restFade * 0.55;

                return blended + glass;
            }
        """
    }
}
