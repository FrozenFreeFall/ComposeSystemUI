package com.example.simplesystemui.platform

import android.view.WindowManager

/** AOSP Android 16 WindowManager.LayoutParams constants (some are hidden from public SDK). */
object SystemWindowTypes {
//    const val TYPE_STATUS_BAR = 2000
    const val TYPE_STATUS_BAR = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
//    const val TYPE_NAVIGATION_BAR = 2019
    const val TYPE_NAVIGATION_BAR = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
//    const val TYPE_NOTIFICATION_SHADE = 2040
    const val TYPE_NOTIFICATION_SHADE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
}
