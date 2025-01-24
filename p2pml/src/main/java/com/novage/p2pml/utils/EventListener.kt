package com.novage.p2pml.utils

fun interface EventListener<T> {
    fun onEvent(data: T)
}
