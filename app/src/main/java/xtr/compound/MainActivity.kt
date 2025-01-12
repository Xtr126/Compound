package xtr.compound

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.PixelCopy
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.topjohnwu.superuser.Shell
import xtr.compound.databinding.ActivityFullscreenBinding
import xtr.compound.server.RemoteServiceHelper


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var fullscreenContent: View
    private val hideHandler = Handler(Looper.myLooper()!!)
    private var mService: IRemoteService? = null

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

        isFullscreen = true

        setupRecyclerViewAdapters()

        setupButtonTint()

        setupButtonActions()

        blurTaskbarBackground()

        setBlurRadius(30f)
        Shell.getShell{
            RemoteServiceHelper.getInstance(this) { mService = it }
        }
    }

    private fun setupRecyclerViewAdapters() {
        findViewById<RecyclerView>(R.id.taskbarApps).setAdapter(
            AppsGridAdapter(this, R.layout.app_taskbar_item, binding.appsContainer)
        )

        binding.startMenu.appsList.setAdapter(AppsGridAdapter(this, R.layout.app_list_item, binding.appsContainer))
        binding.startMenu.appsGrid.setAdapter(AppsGridAdapter(this, R.layout.app_grid_item, binding.appsContainer))
    }


    private fun setupButtonTint(iconTint: ColorStateList? = binding.startMenu.powerButton.iconTint) {
        binding.taskbar.startMenuButton.imageTintList = iconTint
    }

    private fun setBlurRadius(radius: Float) {
        setBlurRadius(binding.startMenu.background, radius)
        setBlurRadius(binding.taskbarBackground, radius)
    }

    private fun setBlurRadius(imageView: ImageView, radius: Float) {
        imageView.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
    }

    private fun setupButtonActions() {
        binding.startMenu.settingsButton.setOnClickListener {
            // Send user to the device settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        binding.startMenu.folderButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Environment.getExternalStorageDirectory().path.toUri(), "*/*")
            startActivity(intent)
        }

        binding.startMenu.powerButton.setOnClickListener {
            mService?.showPowerMenu()
        }

        binding.taskbar.startMenuButton.setOnClickListener { onStartMenuButtonClicked() }
        binding.taskbar.homeButton.setOnClickListener{ showDesktop() }
        binding.taskbar.backButton.setOnClickListener{ TODO("Not yet implemented") }
        binding.taskbar.recentsButton.setOnClickListener{ showRecents() }
    }

    private fun showDesktop() {
        TODO("Not yet implemented")
    }

    private fun showRecents() {
        TODO("Not yet implemented")
    }

    private fun blurTaskbarBackground() {
        binding.taskbarBackground.post {
            pixelCopyForBlur(binding.taskbar.root, binding.taskbarBackground)
        }
    }


    private fun onStartMenuButtonClicked() {
        val startMenuView = binding.startMenu.root
        val imageView = binding.startMenu.background

        if (startMenuView.visibility == View.INVISIBLE) {
            pixelCopyForBlur(startMenuView, imageView)
        } else {
            startMenuView.visibility = View.INVISIBLE
        }
        startMenuView.animate()
    }

    private fun pixelCopyForBlur(overlayView: View, imageView: ImageView) {
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
                        overlayView.visibility = View.VISIBLE
                    }
                }

            PixelCopy.request(window, srcRect, bitmap, listener, Handler(Looper.getMainLooper()))
        }


    }

    override fun onResume() {
        super.onResume()
        delayedHide(100)
    }

    private fun hide() {
        // Hide UI first
        supportActionBar?.hide()
        isFullscreen = false

        // Schedule a runnable to remove the status and navigation bar after a delay
        hideHandler.postDelayed(hidePart2Runnable, UI_ANIMATION_DELAY.toLong())
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

        init {
            // Set settings before the main shell can be created
            Shell.enableVerboseLogging = BuildConfig.DEBUG;
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                        .setTimeout(10)
            )
        }
    }



}