package io.github.halilkhrmn.dpimech.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface EngineState {
    data object Stopped : EngineState
    data class Starting(val profileId: String) : EngineState
    data class Running(
        val profileId: String,
        val profileName: String,
        val strategyName: String = "",
        /** The automatic strategy is testing strategies for this network right now. */
        val autoTesting: Boolean = false,
    ) : EngineState

    /** The last start failed or the engine died; [message] is user-facing. */
    data class Failed(val profileId: String?, val message: String) : EngineState

    companion object {
        private val state = MutableStateFlow<EngineState>(Stopped)
        val flow: StateFlow<EngineState> = state.asStateFlow()

        internal fun set(value: EngineState) {
            state.value = value
        }
    }
}
