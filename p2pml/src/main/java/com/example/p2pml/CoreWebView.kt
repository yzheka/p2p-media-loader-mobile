package com.example.p2pml

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient


class CoreWebView(context: Context) {
    private val fileToLoad = "file:///android_asset/core.html"

    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = WebViewClient()
    }

    fun loadCore() {
        webView.loadUrl(fileToLoad)
    }

    fun sendMessage(message: String) {
        webView.evaluateJavascript("javascript:receiveMessageFromAndroid('$message');", null)
    }
}