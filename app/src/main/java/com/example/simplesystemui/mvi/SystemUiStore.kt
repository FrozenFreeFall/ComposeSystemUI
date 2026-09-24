package com.example.simplesystemui.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One service-owned Store, shared by three windows; a single actor serializes intents. */
class SystemUiStore(scope: CoroutineScope) {
    private val intents = Channel<SystemUiIntent>(Channel.UNLIMITED)
    private val effectChannel = Channel<SystemUiEffect>(Channel.BUFFERED)
    private val mutableState = MutableStateFlow(SystemUiState())
    val state = mutableState.asStateFlow()
    // Exactly one consumer (SystemUIService); effects are not replayed on recomposition.
    val effects = effectChannel.receiveAsFlow()
    private val reducerJob = scope.launch {
        for (intent in intents) {
            val result = SystemUiReducer.reduce(mutableState.value, intent)
            mutableState.value = result.state
            result.effects.forEach { effectChannel.send(it) }
        }
    }
    fun dispatch(intent: SystemUiIntent): Boolean = intents.trySend(intent).isSuccess
    fun close() {
        intents.close()
        reducerJob.cancel()
        effectChannel.close()
    }
}
