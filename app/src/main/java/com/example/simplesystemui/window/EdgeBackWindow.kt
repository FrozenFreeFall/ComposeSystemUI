package com.example.simplesystemui.window

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.simplesystemui.BuildConfig
import com.example.simplesystemui.R
import com.example.simplesystemui.platform.SystemWindowTypes
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** A fixed-width input strip and a separate, non-touchable back indicator. */
internal class EdgeBackWindow(
    private val context: Context,
    private val onBack: () -> Unit,
) {
    private var enabled = false
    private var windowManager: WindowManager? = null
    private var inputView: EdgeInputView? = null
    private var inputParams: WindowManager.LayoutParams? = null
    private var indicatorView: EdgeBackIndicatorView? = null
    private var indicatorParams: WindowManager.LayoutParams? = null
    private var screenHeight = 0

    fun attach() {
        if (inputView != null) return
        val display = requireNotNull(
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        )
        val type = SystemWindowTypes.TYPE_EDGE_BACK
        val displayContext = context.createDisplayContext(display)
        val windowContext = if (Build.VERSION.SDK_INT >= 30) {
            displayContext.createWindowContext(type, null)
        } else displayContext
        val themed = ContextThemeWrapper(windowContext, R.style.Theme_SimpleSystemUI)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        screenHeight = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val size = Point().also { display.getRealSize(it) }
            size.y
        }
        val statusHeight = dp(themed, 40)
        val navigationHeight = dp(themed, 64)
        val input = EdgeInputView(themed).apply {
            visibility = if (enabled) View.VISIBLE else View.GONE
            if (Build.VERSION.SDK_INT >= 29) {
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    val exclusionHeight = min(view.height, dp(themed, 200))
                    val top = (view.height - exclusionHeight) / 2
                    view.systemGestureExclusionRects = listOf(
                        Rect(0, top, view.width, top + exclusionHeight),
                    )
                }
            }
        }
        val indicator = EdgeBackIndicatorView(themed).apply { visibility = View.GONE }
        val inputLayout = layoutParams(
            dp(themed, 24), (screenHeight - statusHeight - navigationHeight).coerceAtLeast(1), type,
            touchable = enabled,
        ).apply {
            y = statusHeight
            setTitle("SimpleSystemUI-EDGE_BACK_INPUT")
        }
        val indicatorLayout = layoutParams(dp(themed, 120), dp(themed, 160), type, touchable = false).apply {
            setTitle("SimpleSystemUI-EDGE_BACK_INDICATOR")
        }
        windowManager = wm
        inputView = input
        inputParams = inputLayout
        indicatorView = indicator
        indicatorParams = indicatorLayout
        try {
            wm.addView(input, inputLayout)
            wm.addView(indicator, indicatorLayout)
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        reset()
        val input = inputView ?: return
        val params = inputParams ?: return
        input.visibility = if (enabled) View.VISIBLE else View.GONE
        params.flags = if (enabled) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager?.updateViewLayout(input, params)
    }

    /** Clear an in-flight gesture without sending BACK. */
    fun reset() {
        inputView?.resetGesture()
        indicatorView?.apply {
            visibility = View.GONE
            update(0f, false)
        }
    }

    fun close() {
        reset()
        val wm = windowManager
        indicatorView?.let { runCatching { wm?.removeViewImmediate(it) } }
        inputView?.let { runCatching { wm?.removeViewImmediate(it) } }
        indicatorView = null
        indicatorParams = null
        inputView = null
        inputParams = null
        windowManager = null
        screenHeight = 0
    }

    private fun updateIndicator(distance: Float, rawY: Float, armed: Boolean) {
        val indicator = indicatorView ?: return
        val params = indicatorParams ?: return
        val newY = (rawY - params.height / 2f).roundToInt()
            .coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
        if (params.y != newY) {
            params.y = newY
            windowManager?.updateViewLayout(indicator, params)
        }
        indicator.update(distance.coerceAtLeast(0f), armed)
        indicator.visibility = View.VISIBLE
    }

    private fun layoutParams(width: Int, height: Int, type: Int, touchable: Boolean): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            // LEFT is physical; START would move the gesture to the right in RTL.
            gravity = Gravity.TOP or Gravity.LEFT
            alpha = if (BuildConfig.SYSTEM_UI) 1f else 0.8f
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private inner class EdgeInputView(context: Context) : View(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private val triggerDistance = dp(context, 56).toFloat()
        private var tracking = false
        private var dragging = false
        private var hapticSent = false
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var startX = 0f
        private var startY = 0f

        fun resetGesture() {
            tracking = false
            dragging = false
            hapticSent = false
            pointerId = MotionEvent.INVALID_POINTER_ID
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!enabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    reset()
                    if (event.pointerCount == 1) {
                        tracking = true
                        pointerId = event.getPointerId(0)
                        startX = event.rawX
                        startY = event.rawY
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return true
                    if (event.pointerCount != 1 || event.getPointerId(0) != pointerId) {
                        reset()
                        return true
                    }
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragging) {
                        if (dx > touchSlop && dx > abs(dy)) {
                            dragging = true
                        } else if (dx < -touchSlop || (abs(dy) > touchSlop && abs(dy) >= abs(dx))) {
                            reset()
                            return true
                        }
                    }
                    if (dragging) {
                        val armed = dx >= triggerDistance
                        if (armed && !hapticSent) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            hapticSent = true
                        }
                        updateIndicator(dx, event.rawY, armed)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val commit = tracking && dragging && event.pointerCount == 1 &&
                        event.getPointerId(0) == pointerId && event.rawX - startX >= triggerDistance
                    // Hide and clear before the callback can recreate or change these windows.
                    reset()
                    if (commit) onBack()
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_UP -> reset()
            }
            // This narrow strip owns its DOWN, including sequences rejected as vertical swipes.
            return true
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
