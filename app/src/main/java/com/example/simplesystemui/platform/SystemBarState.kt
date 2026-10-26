package com.example.simplesystemui.platform

/** Android 16 status bar policy, independent of Binder and Compose. */
data class SystemBarState(
    val disabled1: Int = 0,
    val disabled2: Int = 0,
    val appearance: Int = 0,
    val appearanceRegions: List<BarAppearanceRegion> = emptyList(),
    val behavior: Int = 0,
    val requestedVisibleTypes: Int = 3,
    val statusWindowState: Int = 0,
    val navigationWindowState: Int = 0,
    val transientTypes: Int = 0,
    val imeVisible: Boolean = false,
    val imeBackDisposition: Int = 0,
    val showImeSwitcher: Boolean = false,
) {
    val shadeEnabled get() = disabled1 and 0x00010000 == 0 && disabled2 and 4 == 0
    val quickSettingsEnabled get() = shadeEnabled && disabled2 and 1 == 0
    val clockVisible get() = disabled1 and 0x00800000 == 0
    val notificationIconsVisible get() = disabled1 and 0x00020000 == 0
    val systemIconsVisible get() = disabled1 and 0x00100000 == 0 && disabled2 and 2 == 0
    val backDismissesIme get() = imeVisible && imeBackDisposition in 0..2
    val backEnabled get() = backDismissesIme || disabled1 and 0x00400000 == 0
    val homeEnabled get() = disabled1 and 0x00200000 == 0
    val recentsEnabled get() = disabled1 and 0x01000000 == 0
    // WMS may force a bar visible for the IME or split-screen despite the app's request.
    val statusBarVisible get() = transientTypes and 1 != 0 || statusWindowState != 2
    val navigationBarVisible get() = transientTypes and 2 != 0 || navigationWindowState != 2
    val darkNavigationIcons get() = transientTypes and 2 == 0 && appearance and 16 != 0

    fun darkStatusIconsAt(x: Int, y: Int): Boolean {
        if (transientTypes and 1 != 0) return false
        return if (appearanceRegions.isEmpty()) appearance and 8 != 0
        else appearanceRegions.any { it.contains(x, y) && it.appearance and 8 != 0 }
    }
}

data class BarAppearanceRegion(
    val appearance: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom
}
