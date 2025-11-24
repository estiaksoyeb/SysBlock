package com.self.sysblock.features.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

object OverlayController {
    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var circularView: CircularProgressView? = null
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
        
        // Update the custom view
        circularView?.updateProgress(percentage)

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
                circularView?.reset() // Reset state for next time
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun createLayout(context: Context) {
        overlayContainer = FrameLayout(context)
        
        circularView = CircularProgressView(context)
        
        // Center it in the container (100dp size)
        val size = dpToPx(context, 100) 
        val paramsView = FrameLayout.LayoutParams(size, size)
        paramsView.gravity = Gravity.CENTER
        
        overlayContainer?.addView(circularView, paramsView)

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // Click-through
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER
        overlayContainer?.tag = wmParams
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    // --- Custom View for Drawing the Circle ---
    private class CircularProgressView(context: Context) : View(context) {
        private val backgroundPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f
            isAntiAlias = true
            color = Color.parseColor("#40000000") 
        }
        
        private val progressPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            color = Color.YELLOW // Default start color
        }

        /* // --- TIMER TEXT VARIABLES (COMMENTED OUT) ---
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 50f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }
        private var timeText = ""
        */
        
        private var currentProgress = 1.0f
        private var progressAnimator: ValueAnimator? = null
        private val rect = RectF()

        fun reset() {
            currentProgress = 1.0f
            progressPaint.color = Color.YELLOW
            progressAnimator?.cancel()
        }

        fun updateProgress(targetProgress: Float) {
            val newTarget = targetProgress.coerceIn(0f, 1f)

            // If animation is running, cancel it to start new trajectory
            progressAnimator?.cancel()

            // Smoothly animate from current position to new target over 200ms 
            // (matching the SessionManager loop speed)
            progressAnimator = ValueAnimator.ofFloat(currentProgress, newTarget).apply {
                duration = 200 
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    currentProgress = animation.animatedValue as Float
                    updateColor(currentProgress)
                    invalidate()
                }
                start()
            }
            
            /* // --- TIMER TEXT LOGIC (COMMENTED OUT) ---
            // Assuming 15s max for calculation
            val secondsLeft = (newTarget * 15).toInt() + 1
            timeText = secondsLeft.toString()
            */
        }

        private fun updateColor(progress: Float) {
            // Color Logic: Yellow -> Orange -> Red
            progressPaint.color = when {
                progress > 0.4f -> Color.YELLOW
                progress > 0.15f -> Color.parseColor("#FFA500") // Orange
                else -> Color.RED
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val w = width.toFloat()
            val h = height.toFloat()
            val padding = 10f
            
            rect.set(padding, padding, w - padding, h - padding)
            
            // Draw Background
            canvas.drawOval(rect, backgroundPaint)

            // Draw Animated Progress
            val sweepAngle = 360 * currentProgress
            // Start from top (-90)
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
            
            /*
            // --- TIMER TEXT DRAWING (COMMENTED OUT) ---
            val xPos = w / 2
            val yPos = (h / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(timeText, xPos, yPos, textPaint)
            */
        }
    }
}