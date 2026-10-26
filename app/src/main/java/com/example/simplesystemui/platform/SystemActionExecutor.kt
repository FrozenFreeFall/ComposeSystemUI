package com.example.simplesystemui.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.example.simplesystemui.BuildConfig
import com.example.simplesystemui.NavigationAction
import com.example.simplesystemui.SettingsPage

/** 普通应用只用公开 API，系统版本才执行需要系统权限的操作。 */
class SystemActionExecutor(private val context: Context) {
    fun navigate(action: NavigationAction) {
        if (!BuildConfig.SYSTEM_UI) {
            when (action) {
                NavigationAction.BACK -> {
                    check(BackAccessibilityService.connected) { "请在控制台启用全局导航服务" }
                    check(BackAccessibilityService.performBack()) { "系统未接受返回操作" }
                }
                NavigationAction.HOME -> context.startActivity(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                NavigationAction.RECENTS -> {
                    check(BackAccessibilityService.connected) { "请在控制台启用全局导航服务" }
                    check(BackAccessibilityService.performRecents()) { "系统未接受最近任务操作" }
                }
            }
            return
        }
        check(context.checkSelfPermission("android.permission.INJECT_EVENTS") == PackageManager.PERMISSION_GRANTED) {
            "未授予系统 INJECT_EVENTS 权限"
        }
        val key = when (action) {
            NavigationAction.BACK -> KeyEvent.KEYCODE_BACK
            NavigationAction.HOME -> KeyEvent.KEYCODE_HOME
            NavigationAction.RECENTS -> error("系统版最近任务由任务面板处理")
        }
        // injectInputEvent 是隐藏 API，只有系统版本会走到这里。
        val input = context.getSystemService(InputManager::class.java)
        val inject = InputManager::class.java.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val downTime = SystemClock.uptimeMillis()
        fun event(actionCode: Int) = KeyEvent(downTime, SystemClock.uptimeMillis(), actionCode,
            key, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
        var down = false
        var up = false
        try {
            // 0 = ASYNC，不在主线程等待目标应用处理按键。
            down = inject.invoke(input, event(KeyEvent.ACTION_DOWN), 0) as Boolean
        } finally {
            up = inject.invoke(input, event(KeyEvent.ACTION_UP), 0) as Boolean
        }
        check(down && up) { "系统未接受导航按键：$action" }
    }

    @Suppress("DEPRECATION")
    fun setWifi(enabled: Boolean): Boolean {
        check(BuildConfig.SYSTEM_UI) { "普通应用请通过 Wi-Fi 设置页面切换" }
        return context.getSystemService(WifiManager::class.java)?.setWifiEnabled(enabled) ?: false
    }

    fun setBrightness(value: Int) {
        check(Settings.System.canWrite(context)) { "请先在控制台授权修改系统设置" }
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
            SettingsPage.NOTIFICATIONS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        }
        for (action in listOf(requested, Settings.ACTION_SETTINGS).distinct()) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // 部分设备只提供设置首页。
            } catch (_: SecurityException) {
                // 尝试设置首页。
            }
        }
        error("设备没有可用的设置页面")
    }
}
