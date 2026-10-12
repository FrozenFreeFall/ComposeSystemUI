package com.example.simplesystemui.platform

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.wifi.WifiManager
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.example.simplesystemui.mvi.NavigationAction
import com.example.simplesystemui.mvi.SettingsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Privileged side effects. The Store remains independent of Android system services. */
class SystemActionExecutor(private val context: Context) {
    suspend fun navigate(action: NavigationAction) = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission("android.permission.INJECT_EVENTS") != PackageManager.PERMISSION_GRANTED) {
            if (action == NavigationAction.HOME) {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@withContext
            }
            error("普通应用演示不支持返回/最近任务，请使用设备系统导航键")
        }
        val key = when (action) {
            NavigationAction.BACK -> KeyEvent.KEYCODE_BACK
            NavigationAction.HOME -> KeyEvent.KEYCODE_HOME
            NavigationAction.RECENTS -> KeyEvent.KEYCODE_APP_SWITCH
        }
        // @hide API: system UID + INJECT_EVENTS required; public SDK build uses an isolated bridge.
        val input = context.getSystemService(InputManager::class.java)
        val inject = InputManager::class.java.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val downTime = SystemClock.uptimeMillis()
        fun event(actionCode: Int) = KeyEvent(downTime, SystemClock.uptimeMillis(), actionCode,
            key, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
        // 2 = INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH. Never leave a key pressed after failure.
        var up = false
        val down = try {
            inject.invoke(input, event(KeyEvent.ACTION_DOWN), 2) as Boolean
        } finally {
            up = inject.invoke(input, event(KeyEvent.ACTION_UP), 2) as Boolean
        }
        check(down && up) { "系统未接受导航按键：$action" }
    }

    @Suppress("DEPRECATION")
    suspend fun setWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        context.getSystemService(WifiManager::class.java)?.setWifiEnabled(enabled) ?: false
    }

    suspend fun setBrightness(value: Int) = withContext(Dispatchers.IO) {
        check(Settings.System.canWrite(context)) { "未授予 WRITE_SETTINGS" }
        check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) { "无法切换手动亮度模式" }
        check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(10, 255))) {
            "系统亮度写入失败"
        }
    }

    fun openSettings(page: SettingsPage) {
        val requested = when (page) {
            SettingsPage.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsPage.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsPage.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsPage.ALL -> Settings.ACTION_SETTINGS
        }
        for (action in listOf(requested, Settings.ACTION_SETTINGS).distinct()) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Some TV vendors only expose the top-level settings screen.
            } catch (_: SecurityException) {
                // Fall back to the top-level settings screen.
            }
        }
        error("设备没有可用的设置页面")
    }
}
