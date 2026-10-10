package com.paulneja.quorvi.ui.chat.orb

import com.paulneja.quorvi.voice.AssistantViewModel

enum class OrbMode { CHAT, ULTRA }

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING, WORKING }

object OrbStates {
    fun of(phase: AssistantViewModel.Phase): OrbState = when (phase) {
        AssistantViewModel.Phase.LISTENING -> OrbState.LISTENING
        AssistantViewModel.Phase.TRANSCRIBING, AssistantViewModel.Phase.THINKING -> OrbState.THINKING
        AssistantViewModel.Phase.SPEAKING -> OrbState.SPEAKING
        AssistantViewModel.Phase.IDLE, AssistantViewModel.Phase.ERROR -> OrbState.IDLE
    }

    fun clock(nanos: Long, start: Long): Float = ((nanos - start) / 1_000_000_000.0 % 3600.0).toFloat()

    fun detail(sizeDp: Float): Float = when {
        sizeDp < 48f -> 2f
        sizeDp < 120f -> 4f
        else -> 5f
    }
}
