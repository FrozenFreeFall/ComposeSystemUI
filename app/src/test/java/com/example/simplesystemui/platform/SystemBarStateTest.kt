package com.example.simplesystemui.platform

import org.junit.Assert.*
import org.junit.Test

class SystemBarStateTest {
    @Test fun disableFlagsControlOnlyTheirIntendedFeatures() {
        assertFalse(SystemBarState(disabled1 = 0x00010000).shadeEnabled)
        assertFalse(SystemBarState(disabled2 = 0x00000004).shadeEnabled)
        assertTrue(SystemBarState(disabled2 = 0x00000001).shadeEnabled)
        assertFalse(SystemBarState(disabled2 = 0x00000001).quickSettingsEnabled)
        assertFalse(SystemBarState(disabled1 = 0x00800000).clockVisible)
        assertFalse(SystemBarState(disabled1 = 0x00100000).systemIconsVisible)
        assertFalse(SystemBarState(disabled2 = 0x00000002).systemIconsVisible)
        assertFalse(SystemBarState(disabled1 = 0x00400000).backEnabled)
        assertTrue(SystemBarState(disabled1 = 0x00400000).homeEnabled)
        assertFalse(SystemBarState(disabled1 = 0x00200000).homeEnabled)
        assertFalse(SystemBarState(disabled1 = 0x01000000).recentsEnabled)
        assertTrue(SystemBarState(disabled1 = 0x00200000).backEnabled)
    }

    @Test fun transientBarsTemporarilyOverrideHiddenWindowState() {
        val hidden = SystemBarState(statusWindowState = 2, navigationWindowState = 2)
        assertFalse(hidden.statusBarVisible)
        assertFalse(hidden.navigationBarVisible)
        val statusOnly = hidden.copy(transientTypes = 1)
        assertTrue(statusOnly.statusBarVisible)
        assertFalse(statusOnly.navigationBarVisible)
        assertFalse(statusOnly.copy(transientTypes = 0).statusBarVisible)
        assertTrue(hidden.copy(transientTypes = 2).navigationBarVisible)
        // DISABLE2_GLOBAL_ACTIONS (8) must not hide the status bar.
        assertTrue(SystemBarState(disabled2 = 8).statusBarVisible)
    }

    @Test fun wmsForcedVisibilityOverridesTheAppsRequestToHideBars() {
        val fullscreen = SystemBarState(requestedVisibleTypes = 0)
        assertTrue(fullscreen.statusBarVisible)
        assertTrue(fullscreen.navigationBarVisible)
        assertTrue(fullscreen.copy(transientTypes = 1).statusBarVisible)
        assertFalse(fullscreen.copy(statusWindowState = 2).statusBarVisible)
        assertFalse(fullscreen.copy(navigationWindowState = 2).navigationBarVisible)
    }

    @Test fun transientBarUsesLightIconsEvenWhenTheAppRequestsDarkIcons() {
        val lightBackground = SystemBarState(appearance = 8 or 16)
        assertTrue(lightBackground.darkStatusIconsAt(50, 20))
        assertTrue(lightBackground.darkNavigationIcons)
        assertFalse(lightBackground.copy(transientTypes = 1).darkStatusIconsAt(50, 20))
        assertTrue(lightBackground.copy(transientTypes = 1).darkNavigationIcons)
        assertFalse(lightBackground.copy(transientTypes = 2).darkNavigationIcons)
    }

    @Test fun imeDismissBackRemainsAvailableWhenNavigationBackIsDisabled() {
        val disabled = SystemBarState(disabled1 = 0x00400000)
        assertFalse(disabled.backEnabled)
        for (disposition in 0..2) {
            assertTrue(disabled.copy(imeVisible = true, imeBackDisposition = disposition).backEnabled)
        }
        assertFalse(disabled.copy(imeVisible = true, imeBackDisposition = 3).backEnabled)
        assertFalse(disabled.copy(imeVisible = false, imeBackDisposition = 2).backEnabled)
    }

    @Test fun appearanceUsesTheRegionContainingTheIconAndIgnoresEmptyRegions() {
        val state = SystemBarState(appearance = 8, appearanceRegions = listOf(
            BarAppearanceRegion(0, 0, 0, 100, 40),
            BarAppearanceRegion(8, 100, 0, 200, 40),
            BarAppearanceRegion(0, 0, 0, 0, 0),
        ))
        assertFalse(state.darkStatusIconsAt(50, 20))
        assertTrue(state.darkStatusIconsAt(150, 20))
        // Regions are authoritative when present, even outside their covered area.
        assertFalse(state.darkStatusIconsAt(250, 20))
        assertTrue(SystemBarState(appearance = 8).darkStatusIconsAt(50, 20))
        assertFalse(SystemBarState(appearance = 0).darkStatusIconsAt(50, 20))
    }
}
