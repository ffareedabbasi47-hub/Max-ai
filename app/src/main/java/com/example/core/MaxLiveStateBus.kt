package com.example.core

import com.example.data.model.MaxState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live Mode's state, separate from [com.example.ui.viewmodel.MaxViewModel]'s own `_maxState` —
 * [MaxLiveService] is a background Service that can keep running (and change state) whether or
 * not the Activity/ViewModel is currently alive, so it needs a state holder that outlives them.
 * The ViewModel observes this and mirrors it into its own UI state when Live Mode is active.
 */
object MaxLiveStateBus {
    private val _state = MutableStateFlow(MaxState.IDLE)
    val state: StateFlow<MaxState> = _state.asStateFlow()

    private val _isLiveServiceArmed = MutableStateFlow(false)
    val isLiveServiceArmed: StateFlow<Boolean> = _isLiveServiceArmed.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun setState(newState: MaxState) {
        _state.value = newState
    }

    fun setServiceArmed(armed: Boolean) {
        _isLiveServiceArmed.value = armed
    }

    suspend fun emit(event: LiveEvent) {
        _events.emit(event)
    }
}

sealed class LiveEvent {
    data object WakeWordDetected : LiveEvent()
    data class Error(val message: String) : LiveEvent()
}
