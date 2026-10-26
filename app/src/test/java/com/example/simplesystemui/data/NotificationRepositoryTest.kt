package com.example.simplesystemui.data

import android.app.Notification
import android.app.PendingIntent
import android.app.KeyguardManager
import android.content.Intent
import android.os.Process
import android.os.Looper
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NotificationRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val repository = NotificationRepository(context)
    private val owner = Any()
    private val canceled = mutableListOf<List<String>>()

    @After fun stop() = repository.stop()

    private fun connect(vararg notifications: StatusBarNotification) {
        repository.connect(owner, arrayOf(*notifications)) { canceled += it.toList() }
    }

    @Test fun connectionRestoresActiveNotificationsAndSortsNewestFirst() {
        val old = notification(1, "old", time = 10)
        val recent = notification(2, "recent", time = 20)
        connect(old, recent)
        assertTrue(repository.connected)
        assertEquals(listOf(recent.key, old.key), repository.items.map { it.key })
        assertEquals("recent", repository.items.first().title)
    }

    @Test fun postingTheSameKeyUpdatesOneCardAndRemovalDeletesIt() {
        val first = notification(1, "20%")
        connect(first)
        repository.post(owner, notification(1, "80%"))
        assertEquals(1, repository.items.size)
        assertEquals("80%", repository.items.single().title)
        repository.remove(owner, first.key)
        assertTrue(repository.items.isEmpty())
    }

    @Test fun flatListOmitsGroupSummariesAndOnlyOurExactServiceNotification() {
        val service = notification(100, "service", packageName = context.packageName)
        val ownMessage = notification(101, "own message", packageName = context.packageName)
        val otherService = notification(100, "other app")
        val summary = notification(2, "summary", flags = Notification.FLAG_GROUP_SUMMARY)
        connect(service, ownMessage, otherService, summary)
        assertEquals(setOf(ownMessage.key, otherService.key), repository.items.map { it.key }.toSet())
        repository.post(owner, notification(101, "now a summary", packageName = context.packageName,
            flags = Notification.FLAG_GROUP_SUMMARY))
        assertEquals(listOf(otherService.key), repository.items.map { it.key })
    }

    @Test fun clearAllRequestsOnlyClearableKeysAndWaitsForSystemRemoval() {
        val clearable = notification(1, "message")
        val ongoing = notification(2, "playing", flags = Notification.FLAG_ONGOING_EVENT)
        connect(clearable, ongoing)
        repository.dismiss(ongoing.key)
        assertTrue(canceled.isEmpty())
        repository.dismissAll()
        assertEquals(listOf(listOf(clearable.key)), canceled)
        assertEquals(2, repository.items.size)
        repository.remove(owner, clearable.key)
        assertEquals(listOf(ongoing.key), repository.items.map { it.key })
    }

    @Test fun disconnectClearsContentAndOldCallbacksCannotChangeNewConnection() {
        connect(notification(1, "old"))
        repository.disconnect(owner)
        assertFalse(repository.connected)
        assertTrue(repository.items.isEmpty())
        val replacement = Any()
        val fresh = notification(2, "fresh")
        repository.connect(replacement, arrayOf(fresh)) { canceled += it.toList() }
        repository.post(owner, notification(3, "late"))
        repository.remove(owner, fresh.key)
        repository.disconnect(owner)
        assertTrue(repository.connected)
        assertEquals(listOf(fresh.key), repository.items.map { it.key })
    }

    @Test fun reconnectReplacesSnapshotInsteadOfKeepingObsoleteCards() {
        connect(notification(1, "gone"))
        val fresh = notification(2, "fresh")
        connect(fresh)
        assertEquals(listOf(fresh.key), repository.items.map { it.key })
    }

    @Test fun failedCancellationKeepsTheCardAndReportsAnError() {
        val message = notification(1, "keep me")
        repository.connect(owner, arrayOf(message)) { throw SecurityException("access revoked") }
        repository.dismiss(message.key)
        assertEquals(listOf(message.key), repository.items.map { it.key })
        assertTrue(repository.error.orEmpty().contains("access revoked"))
    }

    @Test fun contentIntentAndAutoCancelBelongToTheCurrentNotification() {
        val intent = PendingIntent.getActivity(context, 1, Intent("test.OPEN"), PendingIntent.FLAG_IMMUTABLE)
        val message = notification(1, "open", flags = Notification.FLAG_AUTO_CANCEL, intent = intent)
        connect(message)
        assertEquals(intent, repository.contentIntent(message.key))
        assertTrue(repository.shouldAutoCancel(message.key))
        repository.post(owner, notification(1, "updated without action"))
        assertNull(repository.contentIntent(message.key))
        assertFalse(repository.shouldAutoCancel(message.key))
    }

    @Test fun lockedDeviceHidesContentAndBlocksNotificationActions() {
        val intent = PendingIntent.getActivity(context, 2, Intent("test.LOCKED"), PendingIntent.FLAG_IMMUTABLE)
        val message = notification(1, "private", intent = intent)
        connect(message)
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        repository.refreshLockState()
        assertTrue(repository.locked)
        assertTrue(repository.items.isEmpty())
        assertNull(repository.contentIntent(message.key))
        repository.dismissAll()
        assertTrue(canceled.isEmpty())
        keyguard.setKeyguardLocked(false)
        repository.refreshLockState()
        assertEquals(listOf(message.key), repository.items.map { it.key })
    }

    @Test fun screenOffKeepsNewNotificationContentHiddenUntilTheDeviceIsAwake() {
        connect(notification(1, "private"))
        repository.start()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        repository.post(owner, notification(2, "arrived while screen is off"))
        assertTrue(repository.locked)
        assertTrue(repository.items.isEmpty())
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(repository.locked)
        assertEquals(2, repository.items.size)
    }

    private fun notification(
        id: Int, title: String, time: Long = 1, flags: Int = 0,
        packageName: String = "test.chat", intent: PendingIntent? = null,
    ): StatusBarNotification {
        val notification = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText("body").setContentIntent(intent).build()
        notification.flags = notification.flags or flags
        return StatusBarNotification(packageName, packageName, id, null, 12345, 0, 0,
            notification, Process.myUserHandle(), time)
    }
}
