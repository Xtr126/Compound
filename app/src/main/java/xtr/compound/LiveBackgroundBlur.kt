package xtr.compound

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.WindowManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Best-effort bridge to Android's live cross-window blur drawable. It never captures the screen.
 * Reflection is deliberately isolated here because the drawable and ViewRootImpl API are hidden.
 */
class LiveBackgroundBlur(
    private val view: View,
    private val cornerRadiusDp: Float = 0f
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
            registerBlurListener()
            createDrawableIfNeeded()
            updateAvailability()
        }

        override fun onViewDetachedFromWindow(v: View) {
            unregisterBlurListener()
            restoreBackground()
            blurDrawable = null
            fallbackDrawable = null
            updateAvailability()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= 31) {
            view.addOnAttachStateChangeListener(attachListener)
            if (view.isAttachedToWindow) {
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

    /** Apply opacity as a dark translucent tint and blur in density-independent pixels. */
    fun update(opacityPercent: Int, blurRadiusDp: Int) {
        pendingOpacityPercent = opacityPercent.coerceIn(0, 100)
        pendingBlurRadiusDp = blurRadiusDp.coerceIn(0, 100)
        val tint = Color.argb(pendingOpacityPercent * 255 / 100, 0x18, 0x1A, 0x20)
        fallbackDrawable?.setColor(tint)
        blurDrawable?.let { drawable ->
            runCatching { colorMethod?.invoke(drawable, tint) }
            val effectiveRadius = if (isAvailable) pendingBlurRadiusDp else 0
            runCatching { blurRadiusMethod?.invoke(drawable, (effectiveRadius * density).toInt()) }
            runCatching { cornerRadiusMethod?.invoke(drawable, cornerRadiusDp * density) }
        }
        view.invalidate()
    }

    /** Recheck OS-level blur availability; useful when a settings dialog is open. */
    fun refreshAvailability() = updateAvailability()

    private fun updateAvailability() {
        val drawableReady = blurDrawable != null
        val windowEnabled = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { windowManager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
        } else false
        publishAvailability(drawableReady && windowEnabled && view.isAttachedToWindow)
    }

    private fun publishAvailability(value: Boolean) {
        if (isAvailable == value) return
        isAvailable = value
        update(pendingOpacityPercent, pendingBlurRadiusDp)
        onAvailabilityChanged?.invoke()
    }

    private fun createDrawableIfNeeded() {
        if (closed || blurDrawable != null || Build.VERSION.SDK_INT < 31) return
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
            fallbackDrawable = null
            installBackground(drawable)
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
        // Existing background stays beneath the translucent tint; child icons remain on top.
        ownedBackground = LayerDrawable(listOfNotNull(originalBackground, layer).toTypedArray())
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
        restoreBackground()
        blurDrawable = null
        ownedBackground = null
        fallbackDrawable = null
        publishAvailability(false)
        onAvailabilityChanged = null
    }
}
