package com.novage.p2pml.utils

import com.novage.p2pml.CoreEventMap

class EventEmitter {
    private val listeners = mutableMapOf<CoreEventMap<*>, MutableList<(Any?) -> Unit>>()

    fun <T> on(
        event: CoreEventMap<T>,
        listener: (T) -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        val wrapper: (Any?) -> Unit = { data -> listener(data as T) }
        listeners.getOrPut(event) { mutableListOf() }.add(wrapper)
    }

    fun <T> emit(
        event: CoreEventMap<T>,
        data: T,
    ) {
        listeners[event]?.forEach { it(data) }
    }

    fun <T> off(
        event: CoreEventMap<T>,
        listener: (T) -> Unit,
    ) {
        listeners.remove(event)?.removeAll { it == listener }
    }

    fun <T> hasListeners(event: CoreEventMap<T>): Boolean = listeners[event]?.isNotEmpty() ?: false
}
