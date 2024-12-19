package com.novage.p2pml.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class P2PStateManager {
    private var isEngineDisabled = false
    private val mutex = Mutex()

    suspend fun isEngineDisabled(): Boolean =
        mutex.withLock {
            isEngineDisabled
        }

    suspend fun changeP2PEngineStatus(isP2PDisabled: Boolean) =
        mutex.withLock {
            if (isP2PDisabled == isEngineDisabled) return@withLock

            isEngineDisabled = isP2PDisabled
        }

    suspend fun reset() =
        mutex.withLock {
            isEngineDisabled = false
        }
}
