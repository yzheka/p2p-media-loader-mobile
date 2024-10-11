package com.example.p2pml

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebMessageCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WebMessageProtocol(private val webView: WebView) {
    @SuppressLint("RequiresFeature")
    private val channels: Array<WebMessagePortCompat> = WebViewCompat.createWebMessageChannel(webView)
    private val segmentResponseCallbacks = mutableMapOf<String, CompletableDeferred<ByteArray>>()
    private val mutex = Mutex()

    init {
        setupWebMessageCallback()
    }

    @SuppressLint("RequiresFeature")
    private fun setupWebMessageCallback() {
        channels[0].setWebMessageCallback(
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    println("Received message from Native")
                    if(message?.type == 1) {
                        val arrayBuffer = message.arrayBuffer

                        Log.d("CoreWebView", "Received message from  JS: ${arrayBuffer.size}")
                    } else{
                        Log.d("CoreWebView", "Received message from  JS: ${message?.data}")
                    }
                }
            },
        )
    }

    @SuppressLint("RequiresFeature")
    fun sendInitialMessage() {
        val initialMessage = WebMessageCompat("", arrayOf(channels[1]))
        WebViewCompat.postWebMessage(
            webView,
            initialMessage,
            Uri.parse("*")
        )
    }

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray>{
        val deferred = CompletableDeferred<ByteArray>()

        addSegmentResponseCallback(segmentUrl, deferred)

        withContext(Dispatchers.Main) {
            sendSegmentRequest(segmentUrl)
        }

        return deferred
    }

    private fun sendSegmentRequest(segmentUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.requestSegment('$segmentUrl');", null)
    }

    private suspend fun addSegmentResponseCallback(segmentId: String, deferred: CompletableDeferred<ByteArray>) {
        mutex.withLock {
            segmentResponseCallbacks[segmentId] = deferred
        }
    }

    private suspend fun removeSegmentResponseCallback(segmentId: String) {
        mutex.withLock {
            segmentResponseCallbacks.remove(segmentId)
        }
    }


}