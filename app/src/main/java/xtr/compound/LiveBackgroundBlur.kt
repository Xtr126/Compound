package xtr.compound

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.WindowManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Best-effort bridge to Android's live cross-window blur, with an optional legacy fallback.
 * Reflection is deliberately isolated here because the drawable and ViewRootImpl API are hidden.
 */
class LiveBackgroundBlur(
    private val view: View,
    private val cornerRadiusDp: Float = 0f,
    private val legacy: LegacyBackgroundBlur? = null,
    /** Live cross-window blur only works when this view overlays a distinct window. */
    private val allowLiveBlur: Boolean = true
) : AutoCloseable {
    private val windowManager = view.context.getSystemService(WindowManager::class.java)
    private val density = view.resources.displayMetrics.density
    private val originalBackground: Drawable? = view.background
    private var ownedBackground: Drawable? = null
    private var blurDrawable: Drawable? = null
    private var fallbackDrawable: GradientDrawable? = null
    private var pendingOpacityPercent = AppearanceSettings.DEFAULT_OPACITY
    private var pendingBlurRadiusDp = AppearanceSettings.DEFAULT_BLUR_RADIUS
    private var listeningForBlur = false
    private var blurRadiusMethod: Method? = null
    private var cornerRadiusMethod: Method? = null
    private var colorMethod: Method? = null
    private var closed = false

    var onAvailabilityChanged: (() -> Unit)? = null

    var isAvailable: Boolean = false
        private set

    private val blurListener = Consumer<Boolean> { refreshAvailability() }
    private val uiExecutor = Executor { command -> view.post(command) }

    private val attachListener = object : OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (allowLiveBlur) {
                registerBlurListener()
                createDrawableIfNeeded()
            } else {
                installFallback()
            }
            updateAvailability()
        }

        override fun onViewDetachedFromWindow(v: View) {
            unregisterBlurListener()
            restoreBackground()
            blurDrawable = null
            fallbackDrawable = null
            publishAvailability(false)
            legacy?.update(false, 0, 0f)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= 31) {
            view.addOnAttachStateChangeListener(attachListener)
            if (allowLiveBlur && view.isAttachedToWindow) {
                registerBlurListener()
                createDrawableIfNeeded()
            } else {
                installFallback()
            }
            updateAvailability()
        } else {
            installFallback()
            publishAvailability(false)
        }
        update(pendingOpacityPercent, pendingBlurRadiusDp)
    }

    /** Apply a restrained theme tint over the live blur and opacity to its fallback. */
    fun update(opacityPercent: Int, blurRadiusDp: Int) {
        pendingOpacityPercent = opacityPercent.coerceIn(0, 100)
        pendingBlurRadiusDp = blurRadiusDp.coerceIn(0, 100)
        val nightMode = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val tint = fallbackTint(pendingOpacityPercent, nightMode)
        val liveTint = liveTint(pendingOpacityPercent, nightMode)
        fallbackDrawable?.setColor(tint)
        blurDrawable?.let { drawable ->
            runCatching { colorMethod?.invoke(drawable, liveTint) }
            val effectiveRadius = if (isAvailable) pendingBlurRadiusDp else 0
            runCatching { blurRadiusMethod?.invoke(drawable, (effectiveRadius * density).toInt()) }
            runCatching { cornerRadiusMethod?.invoke(drawable, cornerRadiusDp * density) }
        }
        legacy?.update(!isAvailable && !closed && view.isAttachedToWindow, tint, pendingBlurRadiusDp * density)
        view.invalidate()
    }

    /** Recheck OS-level blur availability; useful when a settings dialog is open. */
    fun refreshAvailability() = updateAvailability()

    private fun updateAvailability() {
        val drawableReady = blurDrawable != null
        val windowEnabled = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { windowManager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
        } else false
        val available = drawableReady && windowEnabled && view.isAttachedToWindow && !closed
        if (available) {
            if (view.background !== ownedBackground || ownedBackground !== blurDrawable) {
                installBackground(blurDrawable!!)
            }
        } else {
            if (ownedBackground === blurDrawable) {
                blurDrawable?.let { runCatching { blurRadiusMethod?.invoke(it, 0) } }
                ownedBackground = null
            }
            installFallback()
        }
        publishAvailability(available)
        update(pendingOpacityPercent, pendingBlurRadiusDp)
    }

    private fun publishAvailability(value: Boolean) {
        if (isAvailable == value) return
        isAvailable = value
        update(pendingOpacityPercent, pendingBlurRadiusDp)
        onAvailabilityChanged?.invoke()
    }

    private fun createDrawableIfNeeded() {
        if (!allowLiveBlur || closed || blurDrawable != null || Build.VERSION.SDK_INT < 31) return
        try {
            // The app targets a recent SDK, so these hidden members are blocked on stock builds
            // unless explicitly exempted. Keep the exemptions restricted to the three API classes
            // used by this adapter. Public availability checks remain on WindowManager.
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/view/View;",
                "Landroid/view/ViewRootImpl;",
                "Lcom/android/internal/graphics/drawable/BackgroundBlurDrawable;"
            )
            val rootMethod = View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }
            val root = rootMethod.invoke(view) ?: throw IllegalStateException("View root unavailable")
            val factory = root.javaClass.getDeclaredMethod("createBackgroundBlurDrawable").apply { isAccessible = true }
            val drawable = factory.invoke(root) as? Drawable ?: throw IllegalStateException("Blur drawable unavailable")
            val type = drawable.javaClass
            blurRadiusMethod = type.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
            cornerRadiusMethod = type.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
            colorMethod = type.getMethod("setColor", Int::class.javaPrimitiveType)
            blurDrawable = drawable
            ownedBackground = null
            fallbackDrawable = null
            update(pendingOpacityPercent, pendingBlurRadiusDp)
        } catch (_: ReflectiveOperationException) {
            // Hidden API enforcement or OEM implementation differences: keep the neutral tint fallback.
            blurDrawable = null
            installFallback()
        } catch (_: RuntimeException) {
            blurDrawable = null
            installFallback()
        } catch (_: LinkageError) {
            blurDrawable = null
            installFallback()
        }
        updateAvailability()
    }

    private fun installFallback() {
        if (closed || ownedBackground != null) return
        val fallback = GradientDrawable().apply { cornerRadius = cornerRadiusDp * density }
        fallbackDrawable = fallback
        installBackground(fallback)
        update(pendingOpacityPercent, pendingBlurRadiusDp)
    }

    private fun installBackground(layer: Drawable) {
        // Replace the background while owned: an opaque original would defeat opacity controls.
        ownedBackground = layer
        view.background = ownedBackground
    }

    private fun restoreBackground() {
        blurDrawable?.let {
            runCatching { blurRadiusMethod?.invoke(it, 0) }
            it.setVisible(false, false)
        }
        if (view.background === ownedBackground) view.background = originalBackground
        ownedBackground = null
    }

    private fun registerBlurListener() {
        if (!listeningForBlur && Build.VERSION.SDK_INT >= 31) {
            listeningForBlur = runCatching {
                windowManager?.addCrossWindowBlurEnabledListener(uiExecutor, blurListener)
                windowManager != null
            }.getOrDefault(false)
        }
    }

    private fun unregisterBlurListener() {
        if (listeningForBlur && Build.VERSION.SDK_INT >= 31) {
            runCatching { windowManager?.removeCrossWindowBlurEnabledListener(blurListener) }
            listeningForBlur = false
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        view.removeOnAttachStateChangeListener(attachListener)
        unregisterBlurListener()
        legacy?.close()
        restoreBackground()
        blurDrawable = null
        ownedBackground = null
        fallbackDrawable = null
        publishAvailability(false)
        onAvailabilityChanged = null
    }

    companion object {
        private const val MAX_LIVE_TINT_ALPHA = 0x66

        internal fun fallbackTint(opacityPercent: Int, nightMode: Boolean): Int {
            val alpha = opacityPercent.coerceIn(0, 100) * 255 / 100
            return if (nightMode) argb(alpha, 0x18, 0x1A, 0x20)
            else argb(alpha, 0xF4, 0xF5, 0xF8)
        }

        internal fun liveTint(opacityPercent: Int, nightMode: Boolean): Int {
            // BackgroundBlurDrawable draws its color over the blurred pixels. Applying the full
            // fallback opacity here masks the blur (80% black at the default setting). Keep the
            // live tint translucent while preserving the slider's relative strength.
            val alpha = opacityPercent.coerceIn(0, 100) * MAX_LIVE_TINT_ALPHA / 100
            return if (nightMode) argb(alpha, 0x18, 0x1A, 0x20)
            else argb(alpha, 0xF4, 0xF5, 0xF8)
        }

        private fun argb(alpha: Int, red: Int, green: Int, blue: Int) =
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
