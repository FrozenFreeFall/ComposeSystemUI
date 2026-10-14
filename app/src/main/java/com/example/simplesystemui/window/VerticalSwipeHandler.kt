package com.example.simplesystemui.window

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs

internal enum class SwipeDirection(val sign: Float) {
    DOWN(1f),
    UP(-1f),
}

/** Lets children keep taps until a vertical drag in the requested direction starts. */
internal class VerticalSwipeHandler(
    context: Context,
    private val direction: SwipeDirection,
    private val canStart: () -> Boolean,
    private val onStart: () -> Unit,
    private val onDrag: (distancePx: Float) -> Unit,
    private val onEnd: (cancelled: Boolean, velocityY: Float) -> Unit,
) {
    private val configuration = ViewConfiguration.get(context)
    private val touchSlop = configuration.scaledTouchSlop.toFloat()
    private val maxVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
    private var tracking = false
    private var dragging = false
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var velocityTracker: VelocityTracker? = null

    // A MOVE that starts interception may also reach onTouchEvent.
    private var lastEventTime = Long.MIN_VALUE
    private var lastAction = -1
    private var lastRawX = Float.NaN
    private var lastRawY = Float.NaN
    private var lastPointerCount = -1
    private var lastHistorySize = -1
    private var lastHandled = false
    private var lastIntercepted = false

    fun intercept(event: MotionEvent): Boolean {
        process(event)
        return lastIntercepted
    }

    fun touch(event: MotionEvent): Boolean {
        process(event)
        // Own DOWN when an empty part of Compose did not consume it.
        return lastHandled
    }

    /** Stop tracking without calling onEnd, for window teardown or external changes. */
    fun reset() {
        clearTracking()
        lastEventTime = Long.MIN_VALUE
        lastAction = -1
        lastHandled = false
        lastIntercepted = false
    }

    private fun process(event: MotionEvent) {
        if (event.eventTime == lastEventTime && event.action == lastAction &&
            event.rawX == lastRawX && event.rawY == lastRawY &&
            event.pointerCount == lastPointerCount && event.historySize == lastHistorySize
        ) return
        lastEventTime = event.eventTime
        lastAction = event.action
        lastRawX = event.rawX
        lastRawY = event.rawY
        lastPointerCount = event.pointerCount
        lastHistorySize = event.historySize
        lastHandled = false
        lastIntercepted = false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            clearTracking()
            if (!canStart()) return
            tracking = true
            pointerId = event.getPointerId(0)
            downX = event.rawX
            downY = event.rawY
            velocityTracker = VelocityTracker.obtain()
        }
        if (!tracking) return
        lastHandled = true
        lastIntercepted = dragging
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) {
                    finish(cancelled = true)
                    return
                }
                // getRawX/Y(index) needs API 29; the raw/local offset also works on API 26.
                val dx = event.rawX + event.getX(index) - event.x - downX
                val dy = event.rawY + event.getY(index) - event.y - downY
                val directedDy = dy * direction.sign
                if (!dragging) {
                    if (directedDy > touchSlop && directedDy > abs(dx)) {
                        if (!canStart()) {
                            clearTracking()
                            return
                        }
                        dragging = true
                        onStart()
                    } else if (directedDy < -touchSlop || (abs(dx) > touchSlop && abs(dx) > abs(dy))) {
                        clearTracking()
                        return
                    }
                }
                if (dragging) {
                    lastIntercepted = true
                    onDrag(dy - direction.sign * touchSlop)
                }
            }
            MotionEvent.ACTION_UP -> finish(cancelled = false)
            MotionEvent.ACTION_CANCEL -> finish(cancelled = true)
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    finish(cancelled = true)
                }
            }
        }
    }

    private fun finish(cancelled: Boolean) {
        val wasDragging = dragging
        val velocity = if (!cancelled && wasDragging) {
            velocityTracker?.computeCurrentVelocity(1000, maxVelocity)
            velocityTracker?.getYVelocity(pointerId) ?: 0f
        } else 0f
        clearTracking()
        if (wasDragging) onEnd(cancelled, velocity)
    }

    private fun clearTracking() {
        tracking = false
        dragging = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
