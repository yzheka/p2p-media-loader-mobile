package com.example.p2pml

import android.os.Handler
import android.os.Looper

object Utils {
    fun runOnUiThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }
}