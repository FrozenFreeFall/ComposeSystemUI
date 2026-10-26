package com.example.simplesystemui.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.simplesystemui.SystemUIApplication

/** Shared callbacks; demo is Android-bound, system injects its repository before registration. */
class SimpleNotificationListener : NotificationListenerService() {
    private var repository: NotificationRepository? = null
    private var accepts: (StatusBarNotification) -> Boolean = { true }
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        useRepository((application as SystemUIApplication).notifications)
    }

    fun useRepository(repository: NotificationRepository, accepts: (StatusBarNotification) -> Boolean = { true }) {
        this.repository = repository
        this.accepts = accepts
    }

    override fun onListenerConnected() {
        if (stopped) return
        val repository = repository ?: return
        try {
            repository.connect(this, activeNotifications.filter(accepts).toTypedArray()) { cancelNotifications(it) }
        } catch (failure: Exception) {
            repository.connect(this, emptyArray()) { cancelNotifications(it) }
            repository.reportError("读取通知失败：${failure.cause?.message ?: failure.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (stopped || sbn == null) return
        if (accepts(sbn)) repository?.post(this, sbn) else repository?.remove(this, sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (!stopped && sbn != null) repository?.remove(this, sbn.key)
    }

    override fun onListenerDisconnected() { repository?.disconnect(this) }

    fun stopListening() {
        stopped = true
        repository?.disconnect(this)
        repository = null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
