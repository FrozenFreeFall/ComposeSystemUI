package com.example.simplesystemui.platform

import android.content.Context

/** Ordinary apps never register as the device's SystemUI. */
@Suppress("UNUSED_PARAMETER")
class CommandQueue(context: Context, callbacks: SystemBarCallbacks, onError: (Throwable) -> Unit) {
    fun start() = Unit
    fun touchAutoHide() = Unit
    fun close() = Unit
}
