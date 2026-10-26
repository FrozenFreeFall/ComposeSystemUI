package com.example.simplesystemui.platform

import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import com.example.simplesystemui.SystemUiController
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class CommandQueueTest {
    private val controller = SystemUiController(RuntimeEnvironment.getApplication()) {}
    private var failure: Throwable? = null
    private val queue = CommandQueue(RuntimeEnvironment.getApplication(), controller) { failure = it }

    @After fun close() { queue.close(); controller.close() }

    @Test fun binderCommandsAreQueuedAndScopedToTheDefaultDisplay() {
        transact("disable") { writeInt(1); writeInt(0x00800000); writeInt(0) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.systemBars.clockVisible)
        transact("disable") { writeInt(0); writeInt(0x00800000); writeInt(0) }
        assertTrue(controller.systemBars.clockVisible)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.systemBars.clockVisible)
        assertNull(failure)
    }

    @Test fun closeDiscardsQueuedCommandsAndFutureBinderCalls() {
        transact("animateExpandNotificationsPanel") {}
        queue.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.shadeVisible)
        transact("animateExpandNotificationsPanel") {}
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.shadeVisible)
    }

    @Test fun panelCommandsRespectDisableAndCollapseReleasesFocus() {
        transact("disable") { writeInt(0); writeInt(0x00010000); writeInt(0) }
        transact("animateExpandNotificationsPanel") {}
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.shadeVisible)
        transact("disable") { writeInt(0); writeInt(0); writeInt(0) }
        transact("animateExpandNotificationsPanel") {}
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.shadeVisible)
        transact("animateCollapsePanels") {}
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(controller.panelVisible)
        assertNull(failure)
    }

    @Test fun imeAndWindowStateReadTheAndroid16ArgumentOrder() {
        transact("setImeWindowStatus") { writeInt(0); writeInt(2); writeInt(3); writeBoolean(true) }
        transact("setWindowState") { writeInt(0); writeInt(2); writeInt(2) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.systemBars.imeVisible)
        assertEquals(3, controller.systemBars.imeBackDisposition)
        assertTrue(controller.systemBars.showImeSwitcher)
        assertFalse(controller.systemBars.navigationBarVisible)
        assertNull(failure)
    }

    @Test fun transientBarsAutoHideThroughWmsAndClearTheirLocalOverride() {
        val hiddenDisplays = installWindowManager()
        transact("setWindowState") { writeInt(0); writeInt(1); writeInt(2) }
        transact("showTransient") { writeInt(0); writeInt(1); writeBoolean(true) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.systemBars.statusBarVisible)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2249))
        assertTrue(controller.systemBars.statusBarVisible)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertFalse(controller.systemBars.statusBarVisible)
        assertEquals(listOf(0), hiddenDisplays)
        assertNull(failure)
    }

    @Test fun holdingABarTouchSuspendsAutoHideUntilTheGestureEnds() {
        val hiddenDisplays = installWindowManager()
        transact("showTransient") { writeInt(0); writeInt(3); writeBoolean(false) }
        shadowOf(Looper.getMainLooper()).idle()
        controller.setSystemBarTouchActive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2250))
        assertEquals(3, controller.systemBars.transientTypes)
        assertTrue(hiddenDisplays.isEmpty())
        controller.setSystemBarTouchActive(false)
        queue.touchAutoHide()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2250))
        assertEquals(0, controller.systemBars.transientTypes)
        assertEquals(listOf(0), hiddenDisplays)
        assertNull(failure)
    }

    private fun installWindowManager(): MutableList<Int> {
        val hiddenDisplays = mutableListOf<Int>()
        val type = Class.forName("android.view.IWindowManager")
        val remote = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "hideTransientBars" -> { hiddenDisplays += args!![0] as Int; null }
                else -> null
            }
        }
        Class.forName("android.view.WindowManagerGlobal").getDeclaredField("sWindowManagerService")
            .apply { isAccessible = true }.set(null, remote)
        return hiddenDisplays
    }

    private fun transact(name: String, payload: Parcel.() -> Unit) {
        val field = Class.forName("com.android.internal.statusbar.IStatusBar\$Stub")
            .getDeclaredField("TRANSACTION_$name").apply { isAccessible = true }
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken("com.android.internal.statusbar.IStatusBar")
            parcel.payload()
            assertTrue(queue.transact(field.getInt(null), parcel, null, IBinder.FLAG_ONEWAY))
        } finally { parcel.recycle() }
    }
}
