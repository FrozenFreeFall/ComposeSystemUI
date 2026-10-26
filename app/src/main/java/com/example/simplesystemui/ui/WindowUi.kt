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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
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
            WindowKind.SHADE -> if (controller.recentsVisible) RecentsPanel(controller)
                else NotificationShade(controller, shadeScrollState)
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
    val bars = controller.systemBars
    val background = if (!BuildConfig.SYSTEM_UI) MaterialTheme.colorScheme.background
        else if (bars.transientTypes and 1 != 0) Color(0xE6101521)
        else if (bars.appearance and 1 != 0) {
            if (bars.appearance and 8 != 0) Color(0xFFEBF1FA) else Color(0xFF101521)
        } else Color.Transparent
    Surface(Modifier.fillMaxSize(), color = background) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusBarForeground(controller) {
                Icon(Icons.Rounded.Tv, null, Modifier.size(20.dp))
            }
            StatusBarForeground(controller) { Text("SYSTEM UI", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (bars.clockVisible) StatusBarForeground(controller) { Text(controller.device.date, fontSize = 12.sp) }
            if (bars.notificationIconsVisible && controller.notifications.items.isNotEmpty()) {
                StatusBarForeground(controller) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Notifications, "未清除的通知", Modifier.size(18.dp))
                        Text("${controller.notifications.items.size}", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (bars.systemIconsVisible) {
                SystemStatusIcons(controller)
                StatusBarForeground(controller) {
                    Icon(if (controller.device.wifiEnabled == true) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        "Wi-Fi 开关状态", Modifier.size(18.dp))
                }
                StatusBarForeground(controller) {
                    Text(controller.device.batteryPercent?.let { "$it%${if (controller.device.charging) " · 充电" else ""}" }
                        ?: "外接电源", fontSize = 12.sp)
                }
            }
            if (bars.clockVisible) StatusBarForeground(controller) {
                Text(controller.device.time, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (bars.shadeEnabled) TvAction("快捷面板", Icons.Rounded.Tune, compact = true) { controller.openShade() }
        }
    }
}

/** AppearanceRegion bounds are screen coordinates, including split-screen light/dark areas. */
@Composable
internal fun StatusBarForeground(controller: SystemUiController, content: @Composable () -> Unit) {
    val view = LocalView.current
    var center by remember { mutableStateOf(0 to 0) }
    val tint = if (!BuildConfig.SYSTEM_UI) MaterialTheme.colorScheme.onSurface
        else if (controller.systemBars.darkStatusIconsAt(center.first, center.second)) Color(0xFF101521)
        else Color.White
    Box(Modifier.onGloballyPositioned {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val position = it.positionInWindow()
        center = (location[0] + position.x.toInt() + it.size.width / 2) to
            (location[1] + position.y.toInt() + it.size.height / 2)
    }) {
        CompositionLocalProvider(LocalContentColor provides tint, content = content)
    }
}

@Composable
private fun NavigationBar(controller: SystemUiController) {
    val focus = remember { FocusRequester() }
    val bars = controller.systemBars
    val initialFocus = when {
        bars.homeEnabled -> "home"
        bars.backEnabled -> "back"
        bars.recentsEnabled -> "recents"
        bars.shadeEnabled -> "shade"
        else -> "release"
    }
    LaunchedEffect(controller.inputTarget, initialFocus) {
        if (controller.inputTarget == InputTarget.NAVIGATION) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    val barDark = if (BuildConfig.SYSTEM_UI) !bars.darkNavigationIcons else controller.dark
    SystemUiTheme(barDark) {
    val background = if (!BuildConfig.SYSTEM_UI || bars.appearance and 2 != 0) MaterialTheme.colorScheme.background
        else if (bars.transientTypes and 2 != 0) Color(0xE6101521) else Color.Transparent
    Surface(Modifier.fillMaxSize(), color = background) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (controller.inputTarget == InputTarget.NAVIGATION) "遥控器控制中" else "系统导航", fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (bars.backEnabled) TvAction(if (bars.backDismissesIme) "收起键盘" else "返回",
                if (bars.backDismissesIme) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.ArrowBack,
                if (initialFocus == "back") Modifier.focusRequester(focus) else Modifier, compact = true) {
                controller.navigate(NavigationAction.BACK)
            }
            if (bars.homeEnabled) TvAction("主页", Icons.Rounded.Home,
                if (initialFocus == "home") Modifier.focusRequester(focus) else Modifier, compact = true) {
                controller.navigate(NavigationAction.HOME)
            }
            if (bars.recentsEnabled) TvAction("最近任务", Icons.Rounded.RecentActors,
                if (initialFocus == "recents") Modifier.focusRequester(focus) else Modifier, compact = true) {
                controller.navigate(NavigationAction.RECENTS)
            }
            if (bars.shadeEnabled) TvAction("快捷面板", Icons.Rounded.Dashboard,
                if (initialFocus == "shade") Modifier.focusRequester(focus) else Modifier, compact = true) { controller.openShade() }
            if (bars.imeVisible && bars.showImeSwitcher) TvAction("切换输入法", Icons.Rounded.Keyboard, compact = true) {
                controller.showImePicker()
            }
            TvAction("释放焦点", Icons.AutoMirrored.Rounded.KeyboardReturn,
                if (initialFocus == "release") Modifier.focusRequester(focus) else Modifier, compact = true) { controller.releaseFocus() }
            Spacer(Modifier.weight(1f))
        }
    }
    }
}

@Composable
private fun NotificationShade(controller: SystemUiController, scrollState: ScrollState) {
    val focus = remember { FocusRequester() }
    val quickSettings = controller.systemBars.quickSettingsEnabled
    LaunchedEffect(controller.shadeVisible, quickSettings) {
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
                        TvAction("关闭", Icons.Rounded.Close,
                            if (!quickSettings) Modifier.focusRequester(focus) else Modifier) { controller.closeShade() }
                    }
                    if (quickSettings) {
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
                    } else Text("快捷设置已被系统禁用", fontSize = 14.sp)
                    HorizontalDivider()
                    if (controller.notifications.locked) {
                        Text("解锁后查看通知", fontSize = 16.sp)
                    } else {
                        NotificationsSection(
                            items = controller.notifications.items,
                            connected = controller.notifications.connected,
                            error = controller.notifications.error,
                            onOpen = controller::openNotification,
                            onDismiss = controller::dismissNotification,
                            onClearAll = controller::clearNotifications,
                            onAccessSettings = if (BuildConfig.SYSTEM_UI) null else controller::openNotificationAccessSettings,
                        )
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
