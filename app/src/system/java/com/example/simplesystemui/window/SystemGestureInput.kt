package com.example.simplesystemui.window

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.MotionEvent

/** Only the system flavor can observe touch without creating a touchable overlay window. */
internal class SystemGestureInput(
    private val context: Context,
    private val onMotionEvent: (MotionEvent) -> Boolean,
    private val onBack: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)
    private var monitor: InputMonitor? = null
    private var receiver: InputEventReceiver? = null
    private var dispatching = false
    @Volatile private var closed = false

    fun attach() {
        check(Looper.myLooper() == mainLooper) { "System gesture input must attach on the main Looper" }
        check(!closed) { "System gesture input is closed" }
        if (receiver != null) return
        check(context.checkSelfPermission("android.permission.MONITOR_INPUT") == PackageManager.PERMISSION_GRANTED) {
            "未授予系统 MONITOR_INPUT 权限"
        }
        val input = checkNotNull(context.getSystemService(InputManager::class.java)) { "系统没有输入管理器" }
        var createdMonitor: InputMonitor? = null
        var createdReceiver: InputEventReceiver? = null
        try {
            // Only this method is absent from the public InputManager API; do not replace its class.
            createdMonitor = InputManager::class.java.getMethod(
                "monitorGestureInput", String::class.java, Int::class.javaPrimitiveType,
            ).invoke(input, "SimpleSystemUI-left-back", Display.DEFAULT_DISPLAY) as InputMonitor
            createdReceiver = object : InputEventReceiver(createdMonitor.inputChannel, mainLooper) {
                override fun onInputEvent(event: InputEvent) {
                    var commitBack = false
                    var acknowledged = false
                    dispatching = true
                    try {
                        // The callback result requests a return action; it is not the input ACK value.
                        if (!closed && event is MotionEvent) commitBack = onMotionEvent(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process monitored gesture", e)
                        cancelGesture()
                    } finally {
                        try {
                            finishInputEvent(event, event is MotionEvent)
                            acknowledged = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to acknowledge monitored input", e)
                            cancelGesture()
                        } finally {
                            dispatching = false
                            // close() during a callback must wait for this event's ACK before disposal.
                            if (closed) releaseResources()
                        }
                    }
                    if (commitBack && acknowledged && !closed) {
                        try {
                            onBack()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to perform monitored back", e)
                            cancelGesture()
                        }
                    }
                }
            }
            monitor = createdMonitor
            receiver = createdReceiver
        } catch (e: Exception) {
            dispose(createdReceiver, createdMonitor)
            Log.e(TAG, "Failed to attach system gesture input", e)
            throw IllegalStateException("无法注册系统手势输入监视器，请检查隐藏 API 和 MONITOR_INPUT 授权", e)
        } catch (e: LinkageError) {
            // A ROM may restrict a hidden class or constructor even after monitor creation succeeds.
            dispose(createdReceiver, createdMonitor)
            Log.e(TAG, "System gesture input API is unavailable", e)
            throw IllegalStateException("目标系统未开放手势输入隐藏 API", e)
        }
    }

    fun pilferPointers(): Boolean {
        if (closed || Looper.myLooper() != mainLooper) {
            Log.w(TAG, "Cannot pilfer pointers on a closed monitor or another Looper")
            return false
        }
        val activeMonitor = monitor
        if (activeMonitor == null) {
            Log.w(TAG, "Cannot pilfer pointers before the monitor is attached")
            return false
        }
        return try {
            activeMonitor.pilferPointers()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pilfer gesture pointers", e)
            false
        }
    }

    fun close() {
        closed = true
        if (Looper.myLooper() == mainLooper) {
            if (!dispatching) releaseResources()
        } else {
            mainHandler.post { if (!dispatching) releaseResources() }
        }
    }

    private fun cancelGesture() {
        try {
            onCancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel monitored gesture", e)
        }
    }

    private fun releaseResources() {
        val oldReceiver = receiver
        val oldMonitor = monitor
        receiver = null
        monitor = null
        dispose(oldReceiver, oldMonitor)
    }

    private fun dispose(oldReceiver: InputEventReceiver?, oldMonitor: InputMonitor?) {
        // InputEventReceiver must be disposed on its Looper before its InputChannel is released.
        try {
            oldReceiver?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispose input receiver", e)
        } finally {
            try {
                oldMonitor?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispose input monitor", e)
            }
        }
    }

    private companion object {
        const val TAG = "SystemGestureInput"
    }
}
