package com.example.simplesystemui

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import com.example.simplesystemui.data.NotificationRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SystemUiControllerNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val repository = NotificationRepository(context)
    private val controller = SystemUiController(context, repository) {}
    private val canceled = mutableListOf<String>()

    @After fun close() = controller.close()

    @Test fun openingWaitsForFocusReleaseAndAutoCancelUsesSystemCancellation() {
        val notification = message(autoCancel = true)
        repository.connect(Any(), arrayOf(notification)) { canceled += it }
        var afterRelease: (() -> Unit)? = null
        controller.runAfterInputReleased = { afterRelease = it }
        controller.openShade()
        controller.openNotification(notification.key)
        assertFalse(controller.shadeVisible)
        assertEquals(InputTarget.APP, controller.inputTarget)
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(canceled.isEmpty())
        requireNotNull(afterRelease).invoke()
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component?.className)
        assertEquals(listOf(notification.key), canceled)
        assertEquals(1, repository.items.size)
    }

    @Test fun closingThePanelDoesNotRemoveNotifications() {
        val notification = message(autoCancel = false)
        repository.connect(Any(), arrayOf(notification)) { canceled += it }
        controller.openShade()
        controller.closeShade()
        assertEquals(1, repository.items.size)
        assertTrue(canceled.isEmpty())
    }

    @Test fun openingUsesTheLatestAutoCancelFlagAfterFocusIsReleased() {
        val source = Any()
        val notification = message(autoCancel = true)
        repository.connect(source, arrayOf(notification)) { canceled += it }
        var afterRelease: (() -> Unit)? = null
        controller.runAfterInputReleased = { afterRelease = it }
        controller.openShade()
        controller.openNotification(notification.key)
        repository.post(source, message(autoCancel = false))
        requireNotNull(afterRelease).invoke()
        assertNotNull(shadowOf(context).nextStartedActivity)
        assertTrue(canceled.isEmpty())
    }

    @Test fun canceledContentIntentReportsFailureAndDoesNotClearNotification() {
        val notification = message(autoCancel = true)
        repository.connect(Any(), arrayOf(notification)) { canceled += it }
        notification.notification.contentIntent.cancel()
        controller.openShade()
        controller.openNotification(notification.key)
        assertTrue(controller.message.orEmpty().contains("通知已失效"))
        assertTrue(canceled.isEmpty())
        assertEquals(1, repository.items.size)
    }

    private fun message(autoCancel: Boolean): StatusBarNotification {
        val pending = PendingIntent.getActivity(context, 77, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, "messages").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A message").setContentIntent(pending).setAutoCancel(autoCancel).build()
        return StatusBarNotification("test.chat", "test.chat", 1, null, 12345, 0, 0,
            notification, Process.myUserHandle(), 1)
    }
}
