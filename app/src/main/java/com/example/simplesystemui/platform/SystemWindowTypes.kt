package com.example.simplesystemui.platform

import android.view.WindowManager
import com.example.simplesystemui.BuildConfig

/** AOSP Android 16 WindowManager.LayoutParams constants (some are hidden from public SDK). */
object SystemWindowTypes {
    val TYPE_STATUS_BAR = if (BuildConfig.SYSTEM_UI) 2000 else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    val TYPE_NAVIGATION_BAR = if (BuildConfig.SYSTEM_UI) 2019 else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    val TYPE_NOTIFICATION_SHADE = if (BuildConfig.SYSTEM_UI) 2040 else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
}
