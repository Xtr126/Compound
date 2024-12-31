package xtr.compound

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.WindowInsets
import android.view.WindowMetrics
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.drawToBitmap
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Visibility
import xtr.compound.databinding.ActivityFullscreenBinding

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var fullscreenContent: View
    private val hideHandler = Handler(Looper.myLooper()!!)

    @SuppressLint("InlinedApi")
    private val hidePart2Runnable = Runnable {
        // Delayed removal of status and navigation bar
        fullscreenContent.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    }

    private var isFullscreen: Boolean = false

    private val hideRunnable = Runnable { hide() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        fullscreenContent = binding.root
        setContentView(fullscreenContent)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isFullscreen = true

        val adapter = AppsGridAdapter(this, R.layout.app_taskbar_item);
        findViewById<RecyclerView>(R.id.taskbarApps).setAdapter(adapter);

        binding.startMenu.appsList.setAdapter(AppsGridAdapter(this, R.layout.app_list_item));
        binding.startMenu.appsGrid.setAdapter(AppsGridAdapter(this, R.layout.app_grid_item));

        binding.startMenuButton.setOnClickListener { onStartMenuButtonClicked() }
        blurTaskbarBackground()
    }

    private fun blurTaskbarBackground() {
        pixelCopyAndBlur(binding.taskbar, binding.taskbarBackground, 40f)
    }


    private fun onStartMenuButtonClicked() {
        val startMenuView = binding.startMenu.root
        val imageView = binding.startMenu.imageView2

        if (startMenuView.visibility == View.INVISIBLE) {
            pixelCopyAndBlur(startMenuView, imageView, 40f)
        } else {
            startMenuView.visibility = View.INVISIBLE
        }
    }

    private fun pixelCopyAndBlur(overlayView: View, imageView: ImageView, radius: Float) {
        overlayView.visibility = View.INVISIBLE
        overlayView.post {
            val bitmap = createBitmap(overlayView.width, overlayView.height)

            val outLocation = IntArray(2)
            overlayView.getLocationInWindow(outLocation)

            val srcRect = Rect(outLocation[0], outLocation[1], outLocation[0] + overlayView.width, outLocation[1] + overlayView.height)

            val listener: PixelCopy.OnPixelCopyFinishedListener =
                PixelCopy.OnPixelCopyFinishedListener { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        imageView.setImageDrawable(bitmap.toDrawable(resources))

                        imageView.setRenderEffect(
                            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                        )

                        overlayView.visibility = View.VISIBLE
                    }
                }

            PixelCopy.request(window, srcRect, bitmap, listener, Handler(Looper.getMainLooper()))
        }


    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100)

    }

    // show or hide the system UI.
    private fun toggle() {
        if (isFullscreen) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        // Hide UI first
        supportActionBar?.hide()
        isFullscreen = false

        // Schedule a runnable to remove the status and navigation bar after a delay
        hideHandler.postDelayed(hidePart2Runnable, UI_ANIMATION_DELAY.toLong())
    }

    private fun show() {
        // Show the system bar
        fullscreenContent.windowInsetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        isFullscreen = true

        // Schedule a runnable to display UI elements after a delay
        hideHandler.removeCallbacks(hidePart2Runnable)
    }

    /**
     * Schedules a call to hide() in [delayMillis], canceling any
     * previously scheduled calls.
     */
    private fun delayedHide(delayMillis: Int) {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
    }

    companion object {
        /**
         * Whether or not the system UI should be auto-hidden after
         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
         */
        private const val AUTO_HIDE = true

        /**
         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
         * user interaction before hiding the system UI.
         */
        private const val AUTO_HIDE_DELAY_MILLIS = 3000

        /**
         * Some older devices needs a small delay between UI widget updates
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300
    }
}