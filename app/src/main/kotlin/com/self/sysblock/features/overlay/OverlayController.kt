package com.self.sysblock.features.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

object OverlayController {
    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var progressBarView: View? = null
    private var isAttached = false
    
    fun init(context: Context) {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
    }

    fun showWarning(context: Context, remainingMs: Long, maxDurationMs: Long) {
        if (windowManager == null) init(context)
        
        if (overlayContainer == null) {
            createLayout(context)
        }

        val percentage = remainingMs.toFloat() / maxDurationMs.toFloat()
        val metrics = context.resources.displayMetrics
        val totalWidth = metrics.widthPixels
        val barWidth = (totalWidth * percentage).toInt()

        val color = if (remainingMs < 5000) Color.RED else Color.YELLOW
        progressBarView?.setBackgroundColor(color)

        val params = progressBarView?.layoutParams
        params?.width = barWidth
        progressBarView?.layoutParams = params

        if (!isAttached) {
            try {
                val wmParams = overlayContainer?.tag as WindowManager.LayoutParams
                windowManager?.addView(overlayContainer, wmParams)
                isAttached = true
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun hide() {
        if (isAttached && overlayContainer != null) {
            try {
                windowManager?.removeView(overlayContainer)
                isAttached = false
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun createLayout(context: Context) {
        overlayContainer = FrameLayout(context)
        progressBarView = View(context)
        
        overlayContainer?.addView(progressBarView, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            20, 
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        overlayContainer?.tag = params 
    }
}