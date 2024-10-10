package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature


class CoreWebView(context: Context) {
    private val fileToLoad = "file:///android_asset/core.html"
    private lateinit var nativePort: WebMessagePortCompat
    private lateinit var jsPort: WebMessagePortCompat


    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = WebViewClientCompat()
        //visibility = View.GONE
    }

    fun destroy() {
        webView.destroy()
    }

    @SuppressLint("RequiresFeature")
    fun loadCore() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL))
        {

            webView.loadUrl(fileToLoad)
            val channels = WebViewCompat.createWebMessageChannel(webView)
            nativePort = channels[0]
            jsPort = channels[1]

            nativePort.setWebMessageCallback(
                object : WebMessagePortCompat.WebMessageCallbackCompat() {
                    override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                       val arrayBuffer = message?.arrayBuffer
                        Log.d("CoreWebView", "Received message from  JS: ${arrayBuffer?.size}")
                    }
                },
            )
        }
    }

    @SuppressLint("RequiresFeature")
    fun sendInitialMessage() {
        val initialMessage = WebMessageCompat("", arrayOf(jsPort))
        WebViewCompat.postWebMessage(
            webView,
            initialMessage,
            Uri.parse("*")
        )
    }

    fun sendMessage(message: String) {
        webView.evaluateJavascript("javascript:receiveMessageFromAndroid('$message');", null)
    }

    fun sendAllStreams(streamsJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseAllStreams('$streamsJSON');", null)
    }

    fun sendStream(streamJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseStream('$streamJSON');", null)
    }

    fun sendSegmentRequest(segmentUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.requestSegment('$segmentUrl');", null)
    }

    fun setManifestUrl(manifestUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.setManifestUrl('$manifestUrl');", null)
    }
}