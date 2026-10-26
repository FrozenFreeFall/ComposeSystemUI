package com.example.simplesystemui.platform

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import com.example.simplesystemui.data.NotificationRepository
import com.example.simplesystemui.data.SimpleNotificationListener

class NotificationAccess internal constructor(
    private val context: Context,
    private val repository: NotificationRepository,
    private val currentUser: () -> Int,
    private val register: (SimpleNotificationListener, Context, ComponentName, Int) -> Unit,
    private val unregister: (SimpleNotificationListener) -> Unit,
) {
    constructor(context: Context, repository: NotificationRepository) : this(
        context, repository,
        { ActivityManager::class.java.getMethod("getCurrentUser").invoke(null) as Int },
        { listener, serviceContext, component, user ->
            NotificationListenerService::class.java.getMethod("registerAsSystemService",
                Context::class.java, ComponentName::class.java, Int::class.javaPrimitiveType)
                .invoke(listener, serviceContext, component, user)
        },
        { listener ->
            NotificationListenerService::class.java.getMethod("unregisterAsSystemService").invoke(listener)
        },
    )

    private var started = false
    private var receiverRegistered = false
    private var listener: SimpleNotificationListener? = null
    private val userSwitches = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (started && intent.action == ACTION_USER_SWITCHED) {
                releaseListener()
                registerCurrentUser()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        try {
            // This protected broadcast comes from Android; dispatch its work on main.
            context.registerReceiver(userSwitches, IntentFilter(ACTION_USER_SWITCHED), null,
                Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED)
            receiverRegistered = true
            registerCurrentUser()
        } catch (failure: Exception) {
            close()
            report(failure)
        }
    }

    private fun registerCurrentUser() {
        try {
            val selectedUser = currentUser()
            val identifier = UserHandle::class.java.getMethod("getIdentifier")
            val fresh = SimpleNotificationListener()
            // System listeners receive other users too: numeric registration is insufficient.
            fresh.useRepository(repository) { notification ->
                val user = identifier.invoke(notification.user) as Int
                user == selectedUser || user == -1 // UserHandle.USER_ALL
            }
            listener = fresh
            register(fresh, context, ComponentName(context, SimpleNotificationListener::class.java), selectedUser)
            // The shared onListenerConnected callback owns the initial snapshot.
        } catch (failure: Exception) {
            releaseListener()
            report(failure)
        }
    }

    private fun releaseListener() {
        val previous = listener ?: return
        listener = null
        // unregisterAsSystemService does not disconnect or suppress queued callbacks.
        previous.stopListening()
        try { unregister(previous) } catch (failure: Exception) { report(failure) }
    }

    fun close() {
        started = false
        releaseListener()
        if (receiverRegistered) {
            receiverRegistered = false
            try { context.unregisterReceiver(userSwitches) } catch (failure: Exception) { report(failure) }
        }
    }

    private fun report(failure: Exception) {
        repository.reportError("连接系统通知服务失败：${failure.cause?.message ?: failure.message}")
    }

    private companion object {
        const val ACTION_USER_SWITCHED = "android.intent.action.USER_SWITCHED"
    }
}
