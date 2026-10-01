package io.github.halilkhrmn.dpimech.engine

import io.github.halilkhrmn.dpimech.core.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live numbers of the running bypass (chart, notification); reset when it stops. */
object EngineStats {
    private val state = MutableStateFlow(TrafficStats())
    val flow: StateFlow<TrafficStats> = state.asStateFlow()

    internal fun update(change: (TrafficStats) -> TrafficStats) {
        state.value = change(state.value)
    }

    internal fun reset(value: TrafficStats = TrafficStats()) {
        state.value = value
    }
}
