package com.example.simplesystemui.mvi

import com.example.simplesystemui.data.DeviceState

enum class InputTarget { APP, NAVIGATION, SHADE }
enum class NavigationAction { BACK, HOME, RECENTS }
enum class SettingsPage { WIFI, BLUETOOTH, DISPLAY, ALL }

data class SystemUiState(
    val device: DeviceState = DeviceState(),
    val inputTarget: InputTarget = InputTarget.APP,
    val shadeReturnTarget: InputTarget = InputTarget.APP,
    val dark: Boolean = true,
    val wifiBusy: Boolean = false,
    val brightness: Int = 128,
    val message: String? = null,
) {
    val shadeVisible: Boolean get() = inputTarget == InputTarget.SHADE
}

sealed interface SystemUiIntent {
    data class DeviceChanged(val device: DeviceState) : SystemUiIntent
    data class BrightnessChanged(val value: Int) : SystemUiIntent
    data object OpenShade : SystemUiIntent
    data object CloseShade : SystemUiIntent
    data object FocusNavigation : SystemUiIntent
    data object ReleaseFocus : SystemUiIntent
    data object BackPressed : SystemUiIntent
    data class Navigate(val action: NavigationAction) : SystemUiIntent
    data object ToggleWifi : SystemUiIntent
    data class WifiFinished(val success: Boolean) : SystemUiIntent
    data object ToggleTheme : SystemUiIntent
    data class AdjustBrightness(val delta: Int) : SystemUiIntent
    data class OpenSettings(val page: SettingsPage) : SystemUiIntent
    data class ReportMessage(val text: String) : SystemUiIntent
    data object StopService : SystemUiIntent
}

sealed interface SystemUiEffect {
    data class Navigate(val action: NavigationAction) : SystemUiEffect
    data class OpenSettings(val page: SettingsPage) : SystemUiEffect
    data class SetWifi(val enabled: Boolean) : SystemUiEffect
    data class SetBrightness(val value: Int) : SystemUiEffect
    data object StopService : SystemUiEffect
}

data class Transition(val state: SystemUiState, val effects: List<SystemUiEffect> = emptyList())
