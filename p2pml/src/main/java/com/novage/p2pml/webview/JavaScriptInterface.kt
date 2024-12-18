package com.novage.p2pml.webview

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class JavaScriptInterface(
    private val coroutineScope: CoroutineScope,
    private val onFullyLoadedCallback: suspend () -> Unit,
) {
    @JavascriptInterface
    fun onWebViewLoaded() {
        coroutineScope.launch {
            onFullyLoadedCallback()
        }
    }
}
