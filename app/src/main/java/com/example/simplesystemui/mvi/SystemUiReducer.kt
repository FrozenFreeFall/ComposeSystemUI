package com.example.simplesystemui.mvi

/** Pure reducer: no Android calls, no coroutines, no mutation. */
object SystemUiReducer {
    fun reduce(state: SystemUiState, intent: SystemUiIntent): Transition = when (intent) {
        is SystemUiIntent.DeviceChanged -> Transition(state.copy(device = intent.device, brightness = intent.device.brightness.coerceIn(10, 255)))
        is SystemUiIntent.BrightnessChanged -> Transition(state.copy(brightness = intent.value.coerceIn(10, 255)))
        SystemUiIntent.OpenShade -> if (state.shadeVisible) Transition(state) else Transition(state.copy(
            inputTarget = InputTarget.SHADE, shadeReturnTarget = state.inputTarget, message = null,
        ))
        SystemUiIntent.CloseShade -> if (!state.shadeVisible) Transition(state) else Transition(state.copy(inputTarget = state.shadeReturnTarget))
        SystemUiIntent.FocusNavigation -> Transition(state.copy(inputTarget = InputTarget.NAVIGATION))
        SystemUiIntent.ReleaseFocus -> Transition(state.copy(inputTarget = InputTarget.APP, shadeReturnTarget = InputTarget.APP))
        SystemUiIntent.BackPressed -> when (state.inputTarget) {
            InputTarget.SHADE -> Transition(state.copy(inputTarget = state.shadeReturnTarget))
            else -> Transition(state.copy(inputTarget = InputTarget.APP))
        }
        is SystemUiIntent.Navigate -> Transition(state.copy(inputTarget = InputTarget.APP), listOf(SystemUiEffect.Navigate(intent.action)))
        SystemUiIntent.ToggleTheme -> Transition(state.copy(dark = !state.dark))
        SystemUiIntent.ToggleWifi -> when {
            state.wifiBusy -> Transition(state)
            state.device.wifiEnabled == null -> Transition(state.copy(message = "设备没有可用的 Wi-Fi 服务"))
            else -> Transition(state.copy(wifiBusy = true, message = null), listOf(SystemUiEffect.SetWifi(!state.device.wifiEnabled)))
        }
        is SystemUiIntent.WifiFinished -> Transition(state.copy(
            wifiBusy = false,
            message = if (intent.success) "Wi-Fi 请求已提交，等待设备状态更新" else "Wi-Fi 切换失败，请检查权限或设备策略",
        ))
        is SystemUiIntent.AdjustBrightness -> Transition(state.copy(
            brightness = (state.brightness + intent.delta).coerceIn(10, 255), message = null,
        )).let { it.copy(effects = listOf(SystemUiEffect.SetBrightness(it.state.brightness))) }
        is SystemUiIntent.OpenSettings -> Transition(state.copy(inputTarget = InputTarget.APP), listOf(SystemUiEffect.OpenSettings(intent.page)))
        is SystemUiIntent.ReportMessage -> Transition(state.copy(message = intent.text))
        SystemUiIntent.StopService -> Transition(state.copy(inputTarget = InputTarget.APP), listOf(SystemUiEffect.StopService))
    }
}
