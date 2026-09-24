package com.example.simplesystemui

import com.example.simplesystemui.data.DeviceState
import com.example.simplesystemui.mvi.*
import org.junit.Assert.*
import org.junit.Test

class SystemUiReducerTest {
    private fun reduce(state: SystemUiState, intent: SystemUiIntent) = SystemUiReducer.reduce(state, intent)

    @Test fun shadeRestoresThePreviousNavigationFocus() {
        val nav = SystemUiState(inputTarget = InputTarget.NAVIGATION)
        val shade = reduce(nav, SystemUiIntent.OpenShade).state
        assertEquals(InputTarget.SHADE, shade.inputTarget)
        assertEquals(InputTarget.NAVIGATION, reduce(shade, SystemUiIntent.BackPressed).state.inputTarget)
    }

    @Test fun repeatedOpenDoesNotOverwriteTheReturnTarget() {
        val initial = SystemUiState()
        val opened = reduce(initial, SystemUiIntent.OpenShade).state
        val repeated = reduce(opened, SystemUiIntent.OpenShade).state
        assertEquals(InputTarget.APP, reduce(repeated, SystemUiIntent.CloseShade).state.inputTarget)
    }

    @Test fun duplicateCloseCannotStealFocusBackFromTheApp() {
        val passive = SystemUiState(inputTarget = InputTarget.APP, shadeReturnTarget = InputTarget.NAVIGATION)
        assertEquals(passive, reduce(passive, SystemUiIntent.CloseShade).state)
    }

    @Test fun navigationReleasesTheWindowBeforeEmittingTheAction() {
        val result = reduce(SystemUiState(inputTarget = InputTarget.NAVIGATION), SystemUiIntent.Navigate(NavigationAction.BACK))
        assertEquals(InputTarget.APP, result.state.inputTarget)
        assertEquals(listOf(SystemUiEffect.Navigate(NavigationAction.BACK)), result.effects)
    }

    @Test fun remoteBackReleasesControlWithoutInjectingAnotherBack() {
        val result = reduce(SystemUiState(inputTarget = InputTarget.NAVIGATION), SystemUiIntent.BackPressed)
        assertEquals(InputTarget.APP, result.state.inputTarget)
        assertTrue(result.effects.isEmpty())
    }

    @Test fun wifiShowsOnlyConfirmedDeviceStateAndCoalescesRepeatedClicks() {
        val initial = SystemUiState(device = DeviceState(wifiEnabled = false))
        val first = reduce(initial, SystemUiIntent.ToggleWifi)
        assertTrue(first.state.wifiBusy)
        assertEquals(false, first.state.device.wifiEnabled)
        assertEquals(listOf(SystemUiEffect.SetWifi(true)), first.effects)
        assertTrue(reduce(first.state, SystemUiIntent.ToggleWifi).effects.isEmpty())
        val failed = reduce(first.state, SystemUiIntent.WifiFinished(false)).state
        assertFalse(failed.wifiBusy)
        assertEquals(false, failed.device.wifiEnabled)
    }

    @Test fun brightnessEffectsStayWithinSafeBounds() {
        assertEquals(listOf(SystemUiEffect.SetBrightness(255)), reduce(SystemUiState(brightness = 250), SystemUiIntent.AdjustBrightness(25)).effects)
        assertEquals(listOf(SystemUiEffect.SetBrightness(10)), reduce(SystemUiState(brightness = 15), SystemUiIntent.AdjustBrightness(-25)).effects)
    }

    @Test fun deviceUpdatesDoNotResetFocusOrTheme() {
        val initial = SystemUiState(inputTarget = InputTarget.SHADE, dark = false)
        val next = reduce(initial, SystemUiIntent.DeviceChanged(DeviceState(time = "12:34", brightness = 100))).state
        assertEquals(InputTarget.SHADE, next.inputTarget)
        assertFalse(next.dark)
        assertEquals(100, next.brightness)
        assertEquals("12:34", next.device.time)
    }
}
