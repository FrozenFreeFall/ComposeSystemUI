package com.example.simplesystemui

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SystemUiControllerCommandsTest {
    private val controller = SystemUiController(RuntimeEnvironment.getApplication()) {}

    @After fun close() = controller.close()

    @Test fun disablingExpansionClosesThePanelAndBlocksEveryOpenCommand() {
        controller.openShade()
        assertTrue(controller.shadeVisible)
        controller.onDisable(0x00010000, 0)
        assertEquals(InputTarget.APP, controller.inputTarget)
        controller.openShade()
        controller.onExpandPanel()
        controller.onTogglePanel()
        assertFalse(controller.panelVisible)
        controller.onDisable(0, 0)
        controller.onExpandPanel()
        assertTrue(controller.shadeVisible)
    }

    @Test fun disablingQuickSettingsKeepsTheNotificationPanelAvailable() {
        controller.onDisable(0, 1)
        controller.onExpandPanel()
        assertTrue(controller.shadeVisible)
        assertFalse(controller.systemBars.quickSettingsEnabled)
    }

    @Test fun hidingNavigationReleasesFocusAndDoesNotRestoreItAfterClosingThePanel() {
        controller.focusNavigation()
        controller.openShade()
        controller.onBarWindowState(2, 2)
        controller.closeShade()
        assertEquals(InputTarget.APP, controller.inputTarget)
        controller.focusNavigation()
        assertEquals(InputTarget.APP, controller.inputTarget)
        controller.onBarWindowState(2, 0)
        controller.focusNavigation()
        assertEquals(InputTarget.NAVIGATION, controller.inputTarget)
        controller.onBarWindowState(2, 2)
        assertEquals(InputTarget.APP, controller.inputTarget)
    }

    @Test fun appearanceAndImeUpdatesDoNotInterruptAnActivePanelDrag() {
        var windowUpdates = 0
        controller.onWindowsChanged = { windowUpdates++ }
        controller.onSystemBarAttributes(8, emptyList(), 0, 3)
        controller.onImeStatus(true, 0, true)
        assertEquals(0, windowUpdates)
        assertEquals(8, controller.systemBars.appearance)
        assertTrue(controller.systemBars.showImeSwitcher)
        controller.onImeStatus(false, 0, true)
        assertFalse(controller.systemBars.showImeSwitcher)
    }
}
