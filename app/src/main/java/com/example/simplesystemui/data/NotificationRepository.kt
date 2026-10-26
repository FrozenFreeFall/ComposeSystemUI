package com.example.simplesystemui.data

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val icon: Icon?,
    val clearable: Boolean,
    val canOpen: Boolean,
)

/** A small main-thread list. Android remains the owner of the actual notifications. */
class NotificationRepository(context: Context) {
    private val context = context.applicationContext
    private val active = linkedMapOf<String, StatusBarNotification>()
    private var owner: Any? = null
    private var cancel: ((Array<String>) -> Unit)? = null
    private var currentItems by mutableStateOf<List<NotificationItem>>(emptyList())
    var connected by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var locked by mutableStateOf(false)
        private set
    val items get() = if (locked) emptyList() else currentItems
    private var observingLock = false
    private var screenOff = false
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOff = intent.action == Intent.ACTION_SCREEN_OFF
            refreshLockState()
        }
    }

    fun start() {
        screenOff = context.getSystemService(PowerManager::class.java)?.isInteractive == false
        refreshLockState()
        if (observingLock) return
        ContextCompat.registerReceiver(context, lockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        observingLock = true
    }

    fun stop() {
        if (observingLock) context.unregisterReceiver(lockReceiver)
        observingLock = false
    }

    fun refreshLockState() {
        locked = screenOff || context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    internal fun connect(source: Any, snapshot: Array<StatusBarNotification>, cancel: (Array<String>) -> Unit) {
        owner = source
        this.cancel = cancel
        active.clear()
        snapshot.filter(::include).forEach { active[it.key] = it }
        connected = true
        error = null
        refreshLockState()
        publish()
    }

    internal fun post(source: Any, notification: StatusBarNotification) {
        if (owner !== source) return
        if (include(notification)) active[notification.key] = notification else active.remove(notification.key)
        refreshLockState()
        publish()
    }

    internal fun remove(source: Any, key: String) {
        if (owner !== source) return
        active.remove(key)
        publish()
    }

    internal fun disconnect(source: Any) {
        if (owner !== source) return
        owner = null
        cancel = null
        connected = false
        active.clear()
        currentItems = emptyList()
        error = null
    }

    fun contentIntent(key: String): PendingIntent? = if (locked) null else active[key]?.notification?.contentIntent

    fun shouldAutoCancel(key: String): Boolean = active[key]?.let {
        it.isClearable && it.notification.flags and Notification.FLAG_AUTO_CANCEL != 0
    } == true

    fun dismiss(key: String) = requestCancellation(listOf(key))

    fun dismissAll() = requestCancellation(active.keys.toList())

    fun reportError(message: String) { error = message }

    private fun requestCancellation(keys: List<String>) {
        refreshLockState()
        if (!connected || locked) return
        val clearable = keys.filter { active[it]?.isClearable == true }.toTypedArray()
        if (clearable.isEmpty()) return
        try {
            cancel?.invoke(clearable)
            error = null
        } catch (failure: Exception) {
            error = "清除通知失败：${failure.cause?.message ?: failure.message}"
        }
        // Keep cards until Android confirms removal through the listener callback.
    }

    private fun include(sbn: StatusBarNotification): Boolean =
        !(sbn.packageName == context.packageName && sbn.id == 100 && sbn.tag == null) &&
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    private fun publish() {
        currentItems = active.values.sortedWith(compareByDescending<StatusBarNotification> { it.postTime }
            .thenBy { it.key }).map { sbn ->
            val notification = sbn.notification
            val extras = notification.extras
            val appName = runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
            val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
            NotificationItem(sbn.key, sbn.packageName, appName,
                extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(512),
                text.take(4096), sbn.postTime, notification.smallIcon, sbn.isClearable,
                notification.contentIntent != null)
        }
    }
}
