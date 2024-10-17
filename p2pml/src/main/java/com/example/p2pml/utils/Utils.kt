package com.example.p2pml.utils

import android.os.Handler
import android.os.Looper
import com.example.p2pml.Constants.HTTP_PREFIX
import com.example.p2pml.Constants.LOCALHOST

import io.ktor.server.application.ApplicationCall
import okhttp3.Request

object Utils {

    fun getUrl(port: Int, path: String): String {
        return "$HTTP_PREFIX$LOCALHOST:$port/$path"
    }

    fun runOnUiThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    fun copyHeaders(call: ApplicationCall, requestBuilder: Request.Builder) {
        val excludedHeaders = setOf(
            "Host",
            "Content-Length",
            "Connection",
            "Transfer-Encoding",
            "Expect",
            "Upgrade",
            "Proxy-Connection",
            "Keep-Alive",
            "Accept-Encoding"
        )

        for (headerName in call.request.headers.names()) {
            if (headerName !in excludedHeaders) {
                val headerValues = call.request.headers.getAll(headerName)
                if (headerValues != null) {
                    for (headerValue in headerValues) {
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                }
            }
        }
    }
}