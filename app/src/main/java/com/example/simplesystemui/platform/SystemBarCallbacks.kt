package com.example.simplesystemui.platform

import android.graphics.drawable.Icon

data class SystemStatusIcon(val icon: Icon, val visible: Boolean, val description: String, val userId: Int, val level: Int = 0)

/** Called on the main thread, for the default display only. */
interface SystemBarCallbacks {
    val transientAutoHideSuspended: Boolean
    fun onDisable(disabled1: Int, disabled2: Int)
    fun onSystemBarAttributes(appearance: Int, regions: List<BarAppearanceRegion>, behavior: Int, requestedVisibleTypes: Int)
    fun onBarWindowState(window: Int, state: Int)
    fun onTransientBars(types: Int, show: Boolean)
    fun onImeStatus(visible: Boolean, backDisposition: Int, showSwitcher: Boolean)
    fun onStatusIcon(slot: String, icon: SystemStatusIcon?)
    fun onExpandPanel()
    fun onCollapsePanels()
    fun onTogglePanel()
    fun onShowRecents()
    fun onHideRecents()
    fun onToggleRecents()
}
