package com.example.p2pml.webview

import android.content.Context
import android.webkit.JavascriptInterface

class JavaScriptInterface(context: Context, private val onFullyLoadedCallback: () -> Unit) {

    @JavascriptInterface
    fun onWebViewLoaded() {
        onFullyLoadedCallback()
    }
}