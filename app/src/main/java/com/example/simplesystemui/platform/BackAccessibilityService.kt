package com.example.simplesystemui.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 普通版由用户手动启用，仅执行返回，不监听事件或读取窗口内容。 */
class BackAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        serviceInstance = this
        connected = true
        notifyConnectionChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clearConnection()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearConnection()
        super.onDestroy()
    }

    private fun clearConnection() {
        if (serviceInstance !== this) return
        serviceInstance = null
        connected = false
        notifyConnectionChanged()
    }

    companion object {
        var connected by mutableStateOf(false)
            private set

        var onConnectionChanged: (() -> Unit)? = null

        private var serviceInstance: BackAccessibilityService? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun performBack(): Boolean =
            serviceInstance?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        private fun notifyConnectionChanged() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                onConnectionChanged?.invoke()
            } else {
                mainHandler.post { onConnectionChanged?.invoke() }
            }
        }
    }
}
