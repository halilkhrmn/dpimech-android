package io.github.halilkhrmn.dpimech.engine

import io.github.halilkhrmn.dpimech.core.StrategyEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The automatic strategy found [strategy] for [profileId] on [networkKey]; the app saves it. */
data class StrategyLearned(val profileId: String, val networkKey: String, val strategy: StrategyEntry)

object EngineEvents {
    private val learned = MutableSharedFlow<StrategyLearned>(extraBufferCapacity = 8)
    val strategyLearned: SharedFlow<StrategyLearned> = learned.asSharedFlow()

    internal fun emit(e: StrategyLearned) {
        learned.tryEmit(e)
    }
}
