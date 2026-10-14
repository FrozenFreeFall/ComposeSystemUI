package com.example.simplesystemui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.simplesystemui.SystemUiController
import com.example.simplesystemui.data.RecentTask

@Composable
fun RecentsPanel(controller: SystemUiController) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(controller.recentsVisible) {
        if (controller.recentsVisible) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(top = 40.dp, bottom = 64.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("最近任务", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                TvAction(if (controller.recentsLoading) "读取中…" else "刷新", Icons.Rounded.Refresh,
                    Modifier.focusRequester(focus), compact = true) {
                    if (!controller.recentsLoading) controller.refreshRecents()
                }
                TvAction("关闭", Icons.Rounded.Close, compact = true) { controller.closeRecents() }
            }

            controller.recentsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            if (controller.recentsLoading && controller.recentTasks.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (controller.recentTasks.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (controller.recentsLoading) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Text("正在读取最近任务…")
                        } else {
                            Icon(Icons.Rounded.Apps, null, Modifier.size(48.dp))
                            Text(if (controller.recentsError == null) "暂无最近任务" else "无法读取最近任务")
                        }
                    }
                } else {
                    val cardHeight = maxHeight.coerceAtMost(380.dp)
                    val cardWidth = (cardHeight * 0.85f).coerceIn(220.dp, 320.dp)
                    LazyRow(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(controller.recentTasks, key = { it.id }) { task ->
                            RecentTaskCard(
                                task = task,
                                width = cardWidth,
                                height = (cardHeight - 8.dp).coerceAtLeast(0.dp),
                                enabled = !controller.recentsLoading,
                                onOpen = { if (!controller.recentsLoading) controller.openRecentTask(task.id) },
                                onRemove = { if (!controller.recentsLoading) controller.removeRecentTask(task.id) },
                            )
                        }
                    }
                }
            }
            Text("方向键选择 · 确认打开 · 上滑或选择移除 · 返回关闭", fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecentTaskCard(
    task: RecentTask, width: Dp, height: Dp, enabled: Boolean, onOpen: () -> Unit, onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var dragOffset by remember(task.id) { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val icon = remember(task.icon) {
        runCatching { task.icon?.toBitmap(width = 48, height = 48)?.asImageBitmap() }.getOrNull()
    }
    val thumbnail = remember(task.thumbnail) { task.thumbnail?.asImageBitmap() }
    val removeTask by rememberUpdatedState(onRemove)
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.width(width).height(height)
            .graphicsLayer { translationY = dragOffset }
            .pointerInput(task.id, swipeThreshold, enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceAtMost(0f)
                    },
                    onDragEnd = {
                        if (dragOffset <= -swipeThreshold) removeTask()
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().weight(1f).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(3.dp, if (focused) colors.primary else colors.surfaceVariant),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (icon != null) {
                        Image(icon, null, Modifier.size(30.dp))
                    } else {
                        Icon(Icons.Rounded.Apps, null, Modifier.size(30.dp))
                    }
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(Modifier.fillMaxWidth().weight(1f), color = colors.surfaceVariant) {
                    if (thumbnail != null) {
                        Image(thumbnail, "${task.title} 的任务预览", Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit)
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp))
                            Text("暂无预览", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        TvAction("移除", Icons.Rounded.DeleteOutline, Modifier.fillMaxWidth(), compact = true, onClick = onRemove)
    }
}
