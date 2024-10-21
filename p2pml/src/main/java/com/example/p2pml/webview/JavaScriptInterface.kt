package com.example.p2pml.webview

import android.webkit.JavascriptInterface

internal class JavaScriptInterface(private val onFullyLoadedCallback: () -> Unit) {

    @JavascriptInterface
    fun onWebViewLoaded() {
        onFullyLoadedCallback()
    }
}