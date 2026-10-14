package com.example.simplesystemui.window

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared recognition; the caller sends BACK only after acknowledging the input event. */
internal class EdgeBackGesture(
    context: Context,
    private val canStart: (MotionEvent) -> Boolean,
    private val onClaim: () -> Boolean,
    private val onProgress: (distancePx: Float, rawY: Float, armed: Boolean) -> Unit,
    private val onHide: () -> Unit,
    private val onArmed: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val triggerDistance = (56 * context.resources.displayMetrics.density).roundToInt().toFloat()
    private var tracking = false
    private var dragging = false
    private var hapticSent = false
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var startX = 0f
    private var startY = 0f

    /** True only when this UP completes a claimed back gesture. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                if (event.pointerCount == 1 && canStart(event)) {
                    tracking = true
                    pointerId = event.getPointerId(0)
                    startX = event.rawX
                    startY = event.rawY
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                if (event.pointerCount != 1 || event.getPointerId(0) != pointerId) {
                    reset()
                    return false
                }
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                if (!dragging) {
                    if (dx > touchSlop && dx > abs(dy)) {
                        if (!onClaim()) {
                            reset()
                            return false
                        }
                        dragging = true
                    } else if (dx < -touchSlop || (abs(dy) > touchSlop && abs(dy) >= abs(dx))) {
                        reset()
                        return false
                    }
                }
                if (dragging) {
                    val armed = dx >= triggerDistance
                    if (armed && !hapticSent) {
                        hapticSent = true
                        onArmed()
                    }
                    onProgress(dx.coerceAtLeast(0f), event.rawY, armed)
                }
            }
            MotionEvent.ACTION_UP -> {
                val commit = tracking && dragging && event.pointerCount == 1 &&
                    event.getPointerId(0) == pointerId && event.rawX - startX >= triggerDistance
                reset()
                return commit
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> reset()
        }
        return false
    }

    /** Forget the gesture and hide its indicator without committing BACK. */
    fun reset() {
        tracking = false
        dragging = false
        hapticSent = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        startX = 0f
        startY = 0f
        onHide()
    }
}
