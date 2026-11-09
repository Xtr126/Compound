package xtr.compound

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.widget.ImageView

/** On-demand legacy snapshot blur; only the background image receives the render effect. */
class LegacyBackgroundBlur(
    private val image: ImageView,
    private val overlay: View,
    private val window: Window,
    private val hideOverlay: Boolean = true
) : AutoCloseable {
    private val originalImage = image.drawable
    private val originalForeground = image.foreground
    private var snapshot: Bitmap? = null
    private var capturing = false
    private var closed = false
    private var enabled = false
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private var captureFrame: Runnable? = null
    private val captureTimeout = Runnable { cancelCapture() }
    private var restoreAlpha: Float? = null
    private var restoreImageAlpha: Float? = null
    private var tint = 0
    private var radius = 0f

    fun update(enabled: Boolean, tint: Int, radius: Float) {
        this.enabled = enabled
        this.tint = tint
        this.radius = radius
        if (!enabled) {
            cancelCapture()
            image.setImageDrawable(originalImage)
            image.foreground = originalForeground
            image.setRenderEffect(null)
        } else {
            applySnapshot()
            if (snapshot == null) refresh()
        }
    }

    /** Refresh when opening the menu or after geometry changes, without a capture loop. */
    fun refresh() {
        if (closed || !enabled || capturing || !image.isAttachedToWindow || image.width <= 0 ||
            image.height <= 0 || overlay.visibility != View.VISIBLE) return
        capturing = true
        val ticket = ++generation
        if (hideOverlay) {
            restoreAlpha = overlay.alpha
            overlay.alpha = 0f
        }
        restoreImageAlpha = image.alpha
        image.alpha = 0f
        // Never leave controls hidden if frame callbacks or PixelCopy stop arriving.
        handler.postDelayed(captureTimeout, 250)
        var frames = 2
        val frame = object : Runnable {
            override fun run() {
                if (ticket != generation || !capturing) return
                if (closed || !enabled || !image.isAttachedToWindow) {
                    cancelCapture()
                    return
                }
                if (--frames > 0) {
                    image.postOnAnimation(this)
                    return
                }
                val location = IntArray(2)
                image.getLocationOnScreen(location)
                val windowLocation = IntArray(2)
                window.decorView.getLocationOnScreen(windowLocation)
                location[0] -= windowLocation[0]
                location[1] -= windowLocation[1]
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(window, Rect(location[0], location[1],
                        location[0] + image.width, location[1] + image.height), bitmap, { result ->
                        val current = ticket == generation && capturing && !closed && enabled
                        if (ticket == generation) cancelCapture()
                        if (current && result == PixelCopy.SUCCESS) {
                            snapshot = bitmap
                            applySnapshot()
                        } else bitmap.recycle()
                    }, handler)
                } catch (_: IllegalArgumentException) {
                    bitmap.recycle()
                    cancelCapture()
                }
            }
        }
        captureFrame = frame
        image.postOnAnimation(frame)
    }

    private fun cancelCapture() {
        generation++
        capturing = false
        captureFrame?.let(image::removeCallbacks)
        captureFrame = null
        handler.removeCallbacks(captureTimeout)
        restoreOverlay()
    }

    private fun applySnapshot() {
        val bitmap = snapshot ?: return
        image.setImageBitmap(bitmap)
        image.foreground = ColorDrawable(tint)
        image.setRenderEffect(if (radius > 0f)
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
    }

    private fun restoreOverlay() {
        restoreAlpha?.let { overlay.alpha = it }
        restoreAlpha = null
        restoreImageAlpha?.let { image.alpha = it }
        restoreImageAlpha = null
    }

    override fun close() {
        closed = true
        update(false, tint, radius)
        snapshot = null
    }
}
