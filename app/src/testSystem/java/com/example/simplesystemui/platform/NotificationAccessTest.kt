package com.example.simplesystemui.platform

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import com.example.simplesystemui.data.NotificationRepository
import com.example.simplesystemui.data.SimpleNotificationListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class NotificationAccessTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val repository = NotificationRepository(context)
    private var selectedUser = 10
    private val registrations = mutableListOf<Pair<SimpleNotificationListener, Int>>()
    private val removed = mutableListOf<SimpleNotificationListener>()
    private val access = NotificationAccess(context, repository, { selectedUser },
        { listener, _, _, user -> registrations += listener to user },
        { listener ->
            assertFalse("Disconnect before unregistering the Binder", repository.connected)
            listener.onNotificationPosted(notification(999, 10))
            assertTrue(repository.items.isEmpty())
            removed += listener
        })

    @After fun close() { access.close() }

    @Test fun startIsIdempotentAndWaitsForTheListenerCallback() {
        access.start()
        access.start()
        assertEquals(1, registrations.size)
        assertEquals(10, registrations.single().second)
        assertFalse(repository.connected)
    }

    @Test fun acceptsOnlyTheSelectedUserAndUserAllNotifications() {
        access.start()
        val listener = registrations.single().first
        repository.connect(listener, emptyArray()) {}
        listener.onNotificationPosted(notification(1, 10))
        listener.onNotificationPosted(notification(2, 11))
        listener.onNotificationPosted(notification(3, -1))
        assertEquals(setOf("notification-1", "notification-3"), repository.items.map { it.title }.toSet())
    }

    @Test fun closeStopsCallbacksBeforeUnregisterAndIsIdempotent() {
        access.start()
        val listener = registrations.single().first
        repository.connect(listener, emptyArray()) {}
        listener.onNotificationPosted(notification(1, 10))
        access.close()
        access.close()
        listener.onListenerConnected()
        assertFalse(repository.connected)
        assertTrue(repository.items.isEmpty())
        assertEquals(listOf(listener), removed)
    }

    @Test fun partialRegistrationFailureStopsAndUnregistersTheListener() {
        var failedListener: SimpleNotificationListener? = null
        var cleanupCount = 0
        val failing = NotificationAccess(context, repository, { 10 },
            { listener, _, _, _ ->
                failedListener = listener
                repository.connect(listener, emptyArray()) {}
                throw SecurityException("permission denied")
            },
            { listener ->
                cleanupCount++
                assertFalse(repository.connected)
                listener.onNotificationPosted(notification(1, 10))
                assertTrue(repository.items.isEmpty())
            })
        try {
            failing.start()
            assertNotNull(failedListener)
            failedListener!!.onListenerConnected()
            assertFalse(repository.connected)
            assertTrue(repository.items.isEmpty())
            assertTrue(repository.error.orEmpty().contains("permission denied"))
            assertEquals(1, cleanupCount)
        } finally { failing.close() }
        assertEquals(1, cleanupCount)
    }

    @Test fun userSwitchClearsTheOldRegistrationAndAppliesTheNewUserFilter() {
        access.start()
        val first = registrations.single().first
        repository.connect(first, emptyArray()) {}
        first.onNotificationPosted(notification(1, 10))
        selectedUser = 11
        context.sendBroadcast(Intent("android.intent.action.USER_SWITCHED"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(10, 11), registrations.map { it.second })
        assertEquals(listOf(first), removed)
        assertTrue(repository.items.isEmpty())
        assertFalse(repository.connected)
        val second = registrations.last().first
        repository.connect(second, emptyArray()) {}
        first.onNotificationPosted(notification(2, 10))
        second.onNotificationPosted(notification(3, 10))
        second.onNotificationPosted(notification(4, 11))
        assertEquals(listOf("notification-4"), repository.items.map { it.title })
        access.close()
        context.sendBroadcast(Intent("android.intent.action.USER_SWITCHED"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, registrations.size)
    }

    private fun notification(id: Int, user: Int): StatusBarNotification = StatusBarNotification(
        "other.package", "other.package", id, null, 1000, 0, 0,
        Notification.Builder(context, "test").setContentTitle("notification-$id").build(),
        UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType).invoke(null, user) as UserHandle,
        id.toLong(),
    )
}
