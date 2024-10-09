package com.example.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebMessagePort
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.p2pml.CoreWebView
import com.example.p2pml.P2PMLServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request


@UnstableApi
class LoggingDataSource(private val wrappedDataSource: DataSource) : DataSource {
    @OptIn(UnstableApi::class)
    override fun open(dataSpec: DataSpec): Long {
        Log.d("HLSSegmentLogger", "Requesting: ${dataSpec.uri}")
        // Log.d("HLSSegmentLogger", "Requesting segment: $dataSpec")

        return wrappedDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return wrappedDataSource.read(buffer, offset, length)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        wrappedDataSource.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = wrappedDataSource.uri

    override fun close() = wrappedDataSource.close()
}

@UnstableApi
class LoggingDataSourceFactory(private val context: Context) : DataSource.Factory {
    private val baseDataSourceFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return LoggingDataSource(baseDataSourceFactory.createDataSource())
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var coreWebView: CoreWebView

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isPlayerReady = mutableStateOf(false)

        lifecycleScope.launch {
            val p2pServer = P2PMLServer(this@MainActivity)

            coreWebView = p2pServer.coreWebView
            p2pServer.startCoreWebView()
            p2pServer.startServer()

            val manifest =
                p2pServer.getServerManifestUrl("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory).createMediaSource(
                MediaItem.fromUri(manifest)
            )

            player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }

            isPlayerReady.value = true
        }

        setContent {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        if (isPlayerReady.value) {
                            PlayerView(context).apply {
                                player = this@MainActivity.player
                            }
                        } else {
                            FrameLayout(context).apply {
                                val progressBar = ProgressBar(context).apply {
                                    isIndeterminate = true
                                }
                                addView(progressBar)
                            }
                        }
                    }
                )

                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = {
                        coreWebView.webView
                    }
                )

                Button(
                    onClick = {
                        coreWebView.sendInitialMessage()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Send Message to WebView")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
