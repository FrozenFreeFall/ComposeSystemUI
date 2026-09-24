package com.example.simplesystemui

import com.example.simplesystemui.mvi.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemUiStoreTest {
    @Test fun queuedIntentsAreSerializedWithoutLosingThemeToggles() = runTest {
        val store = SystemUiStore(backgroundScope)
        repeat(101) { store.dispatch(SystemUiIntent.ToggleTheme) }
        runCurrent()
        assertFalse(store.state.value.dark)
        store.close()
        assertFalse(store.dispatch(SystemUiIntent.ToggleTheme))
    }

    @Test fun anEffectRunsOnceAndIsNotReplayedAfterRecollection() = runTest {
        val store = SystemUiStore(backgroundScope)
        val effects = mutableListOf<SystemUiEffect>()
        val first = backgroundScope.launch { store.effects.collect { effects += it } }
        store.dispatch(SystemUiIntent.Navigate(NavigationAction.HOME))
        runCurrent()
        assertEquals(listOf(SystemUiEffect.Navigate(NavigationAction.HOME)), effects)
        first.cancel()
        backgroundScope.launch { store.effects.collect { effects += it } }
        runCurrent()
        assertEquals(1, effects.size)
        store.close()
    }
}
