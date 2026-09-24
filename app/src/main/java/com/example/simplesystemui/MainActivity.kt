package com.example.simplesystemui

import android.content.Intent
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
                        Text("Android 16 TV · uid=${Process.myUid()}", fontSize = 16.sp)
                        Text(status.message, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvAction("启动服务", Icons.Rounded.PlayArrow, Modifier.focusRequester(focus)) { command(SystemUIService.ACTION_START) }
                            TvAction("停止服务", Icons.Rounded.Stop) { stopService(Intent(this@MainActivity, SystemUIService::class.java)) }
                            TvAction("关闭控制台", Icons.Rounded.Close) { finish() }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvAction("打开快捷面板", Icons.Rounded.Dashboard) { command(SystemUIService.ACTION_OPEN_SHADE) }
                            TvAction("控制导航栏", Icons.Rounded.Gamepad) { command(SystemUIService.ACTION_FOCUS_NAVIGATION) }
                        }
                        Text("此页面仅用于启动和调试。状态栏、导航栏、快捷面板均由 Service 以独立系统窗口创建，关闭此页面后仍存在。", fontSize = 16.sp)
                        Text("导航栏默认不抢按键。选择“控制导航栏”后可用方向键操作；按返回或“释放焦点”把按键交还前台应用。", fontSize = 14.sp)
                    }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        // Run launches this Activity, which bootstraps the same SystemUIService entry point.
        if (savedInstanceState == null) command(SystemUIService.ACTION_START)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            command(SystemUIService.ACTION_OPEN_SHADE)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun command(action: String) {
        try {
            ContextCompat.startForegroundService(this, Intent(this, SystemUIService::class.java).setAction(action))
        } catch (e: Exception) {
            app.reportStatus(ServiceStatus(false, "启动失败：${e.message}"))
        }
    }
}
