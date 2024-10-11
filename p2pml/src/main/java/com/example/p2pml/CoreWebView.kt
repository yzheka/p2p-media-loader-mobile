package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat


class CoreWebView(
    context: Context,
    private val fileToLoad: String = "file:///android_asset/core.html"
) {
    // TODO: Make vebView private
    @SuppressLint("SetJavaScriptEnabled")
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClientCompat()
        loadUrl(fileToLoad)
        //visibility = View.GONE
    }
    private val webMessageProtocol = WebMessageProtocol(webView)

    fun destroy() {
        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }  // Remove from parent before destroying
            destroy()
        }
    }

    suspend fun requestSegmentBytes(segmentUrl: String) {
        webMessageProtocol.requestSegmentBytes(segmentUrl)
    }

    fun sendInitialMessage() {
        webMessageProtocol.sendInitialMessage()
    }

    fun sendAllStreams(streamsJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseAllStreams('$streamsJSON');", null)
    }

    fun sendStream(streamJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseStream('$streamJSON');", null)
    }

    fun setManifestUrl(manifestUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.setManifestUrl('$manifestUrl');", null)
    }
}