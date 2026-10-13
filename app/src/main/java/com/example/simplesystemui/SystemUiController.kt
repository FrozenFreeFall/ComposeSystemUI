package com.example.simplesystemui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.simplesystemui.data.DeviceRepository
import com.example.simplesystemui.data.DeviceState
import com.example.simplesystemui.platform.SystemActionExecutor

enum class InputTarget { APP, NAVIGATION, SHADE }
enum class NavigationAction { BACK, HOME, RECENTS }
enum class SettingsPage { WIFI, BLUETOOTH, DISPLAY, ALL }

/** 按钮直接调用方法，Compose 直接读取属性，不需要 Flow、Store 或事件队列。 */
class SystemUiController(private val context: Context, private val stopService: () -> Unit) {
    var device by mutableStateOf(DeviceState())
        private set
    var inputTarget by mutableStateOf(InputTarget.APP)
        private set
    var dark by mutableStateOf(true)
        private set
    var brightness by mutableIntStateOf(128)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    val shadeVisible get() = inputTarget == InputTarget.SHADE

    var onWindowsChanged: () -> Unit = {}
    var runAfterInputReleased: ((() -> Unit) -> Unit) = { it() }
    private val devices = DeviceRepository(context)
    private val actions = SystemActionExecutor(context)
    private var shadeReturnTarget = InputTarget.APP
    private var closed = false

    fun start() {
        closed = false
        devices.start {
            device = it
            brightness = it.brightness.coerceIn(10, 255)
        }
    }

    fun openShade() {
        if (shadeVisible) return
        shadeReturnTarget = inputTarget
        message = null
        changeTarget(InputTarget.SHADE)
    }

    fun closeShade() {
        if (shadeVisible) changeTarget(shadeReturnTarget)
    }

    fun focusNavigation() {
        changeTarget(InputTarget.NAVIGATION)
    }

    fun releaseFocus() {
        shadeReturnTarget = InputTarget.APP
        changeTarget(InputTarget.APP)
    }

    fun backPressed() {
        if (shadeVisible) closeShade() else releaseFocus()
    }

    fun navigate(action: NavigationAction) {
        afterReleasingInput { actions.navigate(action) }
    }

    fun toggleWifi() {
        if (!BuildConfig.SYSTEM_UI) {
            openSettings(SettingsPage.WIFI)
            return
        }
        safely {
            val enabled = device.wifiEnabled ?: error("设备没有可用的 Wi-Fi 服务")
            check(actions.setWifi(!enabled)) { "Wi-Fi 切换失败，请检查权限或设备策略" }
            message = "Wi-Fi 请求已提交，等待设备状态更新"
        }
    }

    fun toggleTheme() {
        dark = !dark
    }

    fun adjustBrightness(delta: Int) {
        safely {
            val value = (brightness + delta).coerceIn(10, 255)
            actions.setBrightness(value)
            brightness = value
        }
    }

    fun openSettings(page: SettingsPage) {
        afterReleasingInput { actions.openSettings(page) }
    }

    fun stop() {
        releaseFocus()
        stopService()
    }

    fun showMessage(text: String) {
        message = text
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private fun changeTarget(target: InputTarget) {
        if (inputTarget == target) return
        inputTarget = target
        onWindowsChanged()
    }

    private fun afterReleasingInput(action: () -> Unit) {
        safely {
            releaseFocus()
            runAfterInputReleased { if (!closed) safely(action) }
        }
    }

    private fun safely(action: () -> Unit) {
        message = null
        try {
            action()
        } catch (error: Exception) {
            Log.e("SimpleSystemUI", "系统操作失败", error)
            showMessage(error.cause?.message ?: error.message ?: "系统操作失败")
        }
    }

    fun close() {
        closed = true
        devices.close()
        onWindowsChanged = {}
        runAfterInputReleased = {}
    }
}
