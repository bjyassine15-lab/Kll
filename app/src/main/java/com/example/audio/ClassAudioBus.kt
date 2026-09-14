package com.example.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object ClassAudioBus {
    private val _frames = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frames: SharedFlow<ShortArray> = _frames

    fun emit(frame: ShortArray) {
        _frames.tryEmit(frame)
    }
}
