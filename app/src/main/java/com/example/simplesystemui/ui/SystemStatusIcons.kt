package com.example.simplesystemui.ui

import android.content.Context
import android.os.UserHandle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.simplesystemui.SystemUiController
import com.example.simplesystemui.platform.SystemStatusIcon

@Composable
internal fun SystemStatusIcons(controller: SystemUiController) {
    val context = LocalContext.current
    controller.statusIcons.forEach { (slot, icon) ->
        if (icon.visible) key(slot) {
            val image = remember(icon, context) { loadStatusIcon(context, icon) }
            if (image != null) StatusBarForeground(controller) {
                Image(image, icon.description.ifEmpty { slot }, Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(LocalContentColor.current))
            }
        }
    }
}

private fun loadStatusIcon(context: Context, icon: SystemStatusIcon) = runCatching {
    val userContext = if (icon.userId >= 0) {
        val user = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType).invoke(null, icon.userId)
        Context::class.java.getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
            .invoke(context, user, 0) as Context
    } else context
    val size = (18 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    icon.icon.loadDrawable(userContext)?.apply { level = icon.level }?.toBitmap(size, size)?.asImageBitmap()
}.getOrNull()
