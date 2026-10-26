package com.example.simplesystemui.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.simplesystemui.data.NotificationItem
import java.util.Date
import kotlin.math.abs

/** The shade owns vertical scrolling; this section only handles horizontal dismissal. */
@Composable
fun NotificationsSection(
    items: List<NotificationItem>,
    connected: Boolean,
    error: String?,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    onAccessSettings: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("通知 · ${items.size}", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (items.any { it.clearable }) {
                TvAction("全部清除", Icons.Rounded.ClearAll, compact = true, onClick = onClearAll)
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!connected) {
            Text(if (onAccessSettings != null) "尚未连接通知服务，请在设置中允许通知使用权。"
                else "通知服务尚未连接。", fontSize = 14.sp)
            if (onAccessSettings != null) {
                TvAction("通知使用权设置", Icons.Rounded.Settings, compact = true, onClick = onAccessSettings)
            }
        } else if (items.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, Modifier.size(24.dp))
                    Text("暂无通知", fontSize = 16.sp)
                }
            }
        }
        items.forEach { item ->
            key(item.key) {
                NotificationCard(item, onOpen = { onOpen(item.key) }, onDismiss = { onDismiss(item.key) })
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var dragOffset by remember(item.key, item.clearable) { mutableFloatStateOf(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val appName = item.appName.ifBlank { item.packageName }
    val title = item.title.ifBlank { "通知" }
    val icon = remember(item.icon, context) {
        // The posting app can disappear or provide a resource this process cannot load.
        runCatching { item.icon?.loadDrawable(context)?.toBitmap(48, 48)?.asImageBitmap() }.getOrNull()
    }
    val time = remember(item.postTime, context) {
        DateFormat.getTimeFormat(context).format(Date(item.postTime))
    }
    val swipe = if (item.clearable) Modifier.pointerInput(item.key, threshold) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, amount ->
                change.consume()
                dragOffset += amount
            },
            onDragEnd = {
                val shouldDismiss = abs(dragOffset) >= threshold
                dragOffset = 0f
                if (shouldDismiss) dismiss()
            },
            onDragCancel = { dragOffset = 0f },
        )
    } else Modifier

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = dragOffset }.then(swipe),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(2.dp, if (focused) colors.primary else colors.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (icon != null) {
                            Icon(icon, null, Modifier.size(24.dp), tint = colors.primary)
                        } else {
                            Icon(Icons.Rounded.Notifications, null, Modifier.size(24.dp), tint = colors.primary)
                        }
                        Text(appName, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                        Text(time, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.text.isNotBlank()) {
                        Text(item.text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (item.canOpen) {
                Surface(onClick = onOpen,
                    modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                    shape = RoundedCornerShape(16.dp),
                    color = if (focused) colors.surfaceVariant else colors.surface,
                    content = content)
            } else {
                Surface(modifier = Modifier.weight(1f), color = colors.surface, content = content)
            }
            if (item.clearable) {
                TvAction("清除", Icons.Rounded.Close,
                    Modifier.padding(end = 12.dp).semantics {
                        contentDescription = "清除 $appName 的通知：$title"
                    }, compact = true, onClick = onDismiss)
            }
        }
    }
}
