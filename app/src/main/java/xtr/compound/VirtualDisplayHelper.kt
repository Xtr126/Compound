package xtr.compound

import android.content.Intent
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.View.OnTouchListener
import android.view.WindowManagerGlobal

class VirtualDisplayHelper (
    private val mService: IRemoteService,
    private var width: Int,
    private var height: Int,
    surface: Surface,
    launchIntent: Intent
) : OnTouchListener, View.OnKeyListener, OnGenericMotionListener {

    private var dpi: Int = WindowManagerGlobal.getWindowManagerService().getBaseDisplayDensity(Display.DEFAULT_DISPLAY)

    private var displayId: Int = mService.renderAppToSurface(launchIntent, surface, width, height, dpi)

    private var inputTransformer: InputTransformer = InputTransformer(width, height, displayId)

    fun closeApp() {
        displayId.let { mService.releaseVirtualDisplay(it) }
    }

    fun resizeApp(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        displayId.let { mService.resizeVirtualDisplay(it, width, height, dpi) }
    }

    override fun onGenericMotion(v: View, event: MotionEvent): Boolean {
        InputTransformer.setDisplayId(event, displayId)
        mService.injectInputEvent(event)
        return true
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        InputTransformer.setDisplayId(event, displayId)
        mService.injectInputEvent(event)
        return true
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        inputTransformer.transformTouchEvent(event)
        mService.injectInputEvent(event)
        return true
    }
}