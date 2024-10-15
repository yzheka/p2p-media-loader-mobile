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
import androidx.media3.datasource.DefaultHttpDataSource
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

        WebView.setWebContentsDebuggingEnabled(true)

        lifecycleScope.launch {
            val p2pServer = P2PMLServer(this@MainActivity, lifecycleScope)

            // TODO: Remove this delay
            delay(1000)
            p2pServer.startServer()

            val manifest =
                p2pServer.getServerManifestUrl("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            /*val httpDataSourceFactory = DefaultHttpDataSource.Factory().setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)*/
            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory).createMediaSource(
                MediaItem.fromUri(manifest)
            )

            player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
            p2pServer.setUpPlaybackInfo(player)

            setContent {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = this@MainActivity.player
                            }
                        }
                    )
                }
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        coreWebView.destroy()
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
        player.pause()
    }

}
