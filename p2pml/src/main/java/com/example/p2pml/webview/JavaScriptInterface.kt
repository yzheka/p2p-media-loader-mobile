package com.example.p2pml.webview

import android.webkit.JavascriptInterface

class JavaScriptInterface(private val onFullyLoadedCallback: () -> Unit) {

    @JavascriptInterface
    fun onWebViewLoaded() {
        onFullyLoadedCallback()
    }
}