package com.example.simplesystemui.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplesystemui.BuildConfig
import com.example.simplesystemui.InputTarget
import com.example.simplesystemui.NavigationAction
import com.example.simplesystemui.SettingsPage
import com.example.simplesystemui.SystemUiController
import com.example.simplesystemui.window.WindowKind

@Composable
fun SystemWindowContent(kind: WindowKind, controller: SystemUiController, shadeScrollState: ScrollState) {
    SystemUiTheme(controller.dark) {
        when (kind) {
            WindowKind.STATUS -> StatusBar(controller)
            WindowKind.NAVIGATION -> NavigationBar(controller)
            WindowKind.SHADE -> NotificationShade(controller, shadeScrollState)
        }
    }
}

@Composable
fun SystemUiTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFB8D2FF), onPrimary = Color(0xFF10203A),
        background = Color(0xFF101521), surface = Color(0xFF1C2940),
        onSurface = Color(0xFFEEF3FF), surfaceVariant = Color(0xFF2A3A54),
    ) else lightColorScheme(
        primary = Color(0xFF294E86), background = Color(0xFFEBF1FA),
        surface = Color(0xFFFAFCFF), surfaceVariant = Color(0xFFD9E4F3),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun TvAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (focused) colors.primary else colors.surface, label = "focus")
    Surface(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(if (compact) 8.dp else 16.dp),
        color = bg, contentColor = if (focused) colors.onPrimary else colors.onSurface,
        border = BorderStroke(2.dp, if (focused) colors.onSurface else Color.Transparent)) {
        Row(Modifier.padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 5.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(if (compact) 18.dp else 24.dp))
            Column {
                Text(title, fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusBar(controller: SystemUiController) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Tv, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text("SYSTEM UI", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(controller.device.date, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(if (controller.device.wifiEnabled == true) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                "Wi-Fi 开关状态", Modifier.size(18.dp))
            Text(controller.device.batteryPercent?.let { "$it%${if (controller.device.charging) " · 充电" else ""}" } ?: "外接电源", fontSize = 12.sp)
            Text(controller.device.time, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TvAction("快捷面板", Icons.Rounded.Tune, compact = true) { controller.openShade() }
        }
    }
}

@Composable
private fun NavigationBar(controller: SystemUiController) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(controller.inputTarget) {
        if (controller.inputTarget == InputTarget.NAVIGATION) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (controller.inputTarget == InputTarget.NAVIGATION) "遥控器控制中" else "系统导航", fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TvAction("返回", Icons.AutoMirrored.Rounded.ArrowBack, compact = true) {
                controller.navigate(NavigationAction.BACK)
            }
            TvAction("主页", Icons.Rounded.Home, Modifier.focusRequester(focus), compact = true) {
                controller.navigate(NavigationAction.HOME)
            }
            TvAction("最近任务", Icons.Rounded.RecentActors, compact = true) {
                controller.navigate(NavigationAction.RECENTS)
            }
            TvAction("快捷面板", Icons.Rounded.Dashboard, compact = true) { controller.openShade() }
            TvAction("释放焦点", Icons.AutoMirrored.Rounded.KeyboardReturn, compact = true) { controller.releaseFocus() }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotificationShade(controller: SystemUiController, scrollState: ScrollState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(controller.shadeVisible) {
        if (controller.shadeVisible) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 112.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(scrollState).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("快捷面板", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("${controller.device.time}  ·  ${controller.device.date}", fontSize = 14.sp)
                        }
                        TvAction("关闭", Icons.Rounded.Close) { controller.closeShade() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvAction("Wi-Fi", Icons.Rounded.Wifi, Modifier.weight(1f).focusRequester(focus),
                            subtitle = when {
                                !BuildConfig.SYSTEM_UI -> "打开 Wi-Fi 设置"
                                controller.device.wifiEnabled == true -> "已开启 · 点击关闭"
                                controller.device.wifiEnabled == false -> "已关闭 · 点击开启"
                                else -> "设备不支持"
                            }) { controller.toggleWifi() }
                        TvAction("蓝牙", Icons.Rounded.Bluetooth, Modifier.weight(1f), "打开设备蓝牙设置") {
                            controller.openSettings(SettingsPage.BLUETOOTH)
                        }
                        TvAction("外观", Icons.Rounded.DarkMode, Modifier.weight(1f), if (controller.dark) "深色 · 切换" else "浅色 · 切换") {
                            controller.toggleTheme()
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("系统亮度  ${controller.brightness * 100 / 255}%", fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { controller.brightness / 255f }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                TvAction("降低", Icons.Rounded.Remove, Modifier.weight(1f)) { controller.adjustBrightness(-25) }
                                TvAction("提高", Icons.Rounded.Add, Modifier.weight(1f)) { controller.adjustBrightness(25) }
                                TvAction("显示设置", Icons.Rounded.Settings, Modifier.weight(1f)) { controller.openSettings(SettingsPage.DISPLAY) }
                            }
                            Text("电视背光是否响应由厂商实现决定；调节时切换到手动亮度。", fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvAction("系统设置", Icons.Rounded.Settings, Modifier.weight(1f)) { controller.openSettings(SettingsPage.ALL) }
                        TvAction("停止窗口服务", Icons.Rounded.PowerSettingsNew, Modifier.weight(1f)) { controller.stop() }
                    }
                    controller.message?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary) }
                    Text("方向键移动 · 确认执行 · 返回关闭", fontSize = 12.sp)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 64.dp).height(48.dp),
                contentAlignment = Alignment.Center) {
                Text("上滑收起", fontSize = 12.sp)
            }
        }
    }
}
