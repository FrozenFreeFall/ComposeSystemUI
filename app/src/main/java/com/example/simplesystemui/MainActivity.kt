package com.example.simplesystemui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simplesystemui.ui.SystemUiTheme
import com.example.simplesystemui.ui.TvAction

/** Development bootstrap only. Finishing this Activity does NOT remove any system windows. */
class MainActivity : ComponentActivity() {
    private val app get() = application as SystemUIApplication
    private var overlayAllowed by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        overlayAllowed = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        for (intent in listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_SETTINGS),
        )) {
            try { startActivity(intent); return } catch (_: android.content.ActivityNotFoundException) {}
        }
        app.reportStatus(ServiceStatus(false, "请在系统设置中允许本应用显示悬浮窗"))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val status by app.serviceStatus.collectAsStateWithLifecycle()
            val focus = remember { FocusRequester() }
            SystemUiTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("SystemUIService 控制台", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Android ${Build.VERSION.RELEASE} · uid=${Process.myUid()}", fontSize = 16.sp)
                        Text(status.message, fontSize = 16.sp)
                        Text("普通应用演示 · 悬浮窗权限：${if (overlayAllowed) "已授权" else "未授权"}", fontSize = 16.sp)
                        TvAction("授权悬浮窗", Icons.Rounded.Settings) { requestOverlayPermission() }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvAction("显示悬浮窗", Icons.Rounded.PlayArrow, Modifier.focusRequester(focus)) { command(SystemUIService.ACTION_START) }
                            TvAction("停止服务", Icons.Rounded.Stop) { stopService(Intent(this@MainActivity, SystemUIService::class.java)) }
                            TvAction("关闭控制台", Icons.Rounded.Close) { finish() }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvAction("打开快捷面板", Icons.Rounded.Dashboard) { command(SystemUIService.ACTION_OPEN_SHADE) }
                            TvAction("控制导航栏", Icons.Rounded.Gamepad) { command(SystemUIService.ACTION_FOCUS_NAVIGATION) }
                        }
                        Text("演示步骤：授权悬浮窗 → 返回此页 → 显示悬浮窗 → 关闭控制台。状态栏、导航栏和快捷面板由前台服务显示，可通过停止服务移除。", fontSize = 16.sp)
                        Text("普通应用悬浮窗不会替换系统栏。返回/最近任务及 Wi-Fi 开关需要系统权限；亮度调整需要允许修改系统设置。", fontSize = 14.sp)
                        TvAction("授权修改系统设置", Icons.Rounded.BrightnessMedium) {
                            try { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
                            catch (_: android.content.ActivityNotFoundException) { app.reportStatus(ServiceStatus(false, "请在系统设置中允许修改系统设置")) }
                        }
                        Text("导航栏默认不抢按键。选择“控制导航栏”后可用方向键操作；按返回或“释放焦点”把按键交还前台应用。", fontSize = 14.sp)
                    }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            command(SystemUIService.ACTION_OPEN_SHADE)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun command(action: String) {
        if (!Settings.canDrawOverlays(this)) {
            app.reportStatus(ServiceStatus(false, "请先授权悬浮窗，再点击显示悬浮窗"))
            requestOverlayPermission()
            return
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, SystemUIService::class.java).setAction(action))
        } catch (e: Exception) {
            app.reportStatus(ServiceStatus(false, "启动失败：${e.message}"))
        }
    }
}
