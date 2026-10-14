package com.example.simplesystemui.window

import android.content.Context
import android.view.MotionEvent

/** The demo flavor uses its ordinary narrow edge window and has no hidden API dependency. */
@Suppress("UNUSED_PARAMETER")
internal class SystemGestureInput(
    context: Context,
    onMotionEvent: (MotionEvent) -> Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    fun attach() {
        error("系统输入监视器仅支持 system 版本")
    }

    fun pilferPointers(): Boolean = false

    fun close() = Unit
}
