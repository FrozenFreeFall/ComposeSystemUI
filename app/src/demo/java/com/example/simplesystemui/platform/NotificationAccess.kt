package com.example.simplesystemui.platform

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.example.simplesystemui.data.NotificationRepository
import com.example.simplesystemui.data.SimpleNotificationListener

/** Android owns the user-approved demo listener, independently of the window service. */
class NotificationAccess(private val context: Context, private val repository: NotificationRepository) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        try {
            if (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)) {
                return // The shared UI already explains the notification-access setting.
            } else if (!repository.connected) {
                NotificationListenerService.requestRebind(ComponentName(context, SimpleNotificationListener::class.java))
            }
        } catch (failure: Exception) {
            repository.reportError("连接通知服务失败：${failure.cause?.message ?: failure.message}")
        }
    }

    fun close() { started = false }
}
