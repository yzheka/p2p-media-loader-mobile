package com.example.p2pml

class P2PStateManager {
    private var isP2PEngineEnabled = true
    private var onP2PEngineStatusChange: (suspend (state: Boolean) -> Unit)? = null

    fun isP2PEngineEnabled(): Boolean {
        return isP2PEngineEnabled
    }

    fun setOnP2PEngineStatusChange(onP2PEngineStatusChange: (suspend (state: Boolean) -> Unit)){
        this.onP2PEngineStatusChange = onP2PEngineStatusChange
    }

    suspend fun changeP2PEngineStatus(isP2PEngineStatusEnabled: Boolean){
        if(isP2PEngineStatusEnabled == isP2PEngineEnabled) return

        isP2PEngineEnabled = isP2PEngineStatusEnabled
        onP2PEngineStatusChange?.invoke(isP2PEngineStatusEnabled)
    }

}