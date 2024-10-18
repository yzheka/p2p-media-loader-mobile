package com.example.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.p2pml.P2PML
//import com.example.p2pml.P2PMLServer
import kotlinx.coroutines.launch


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
class LoggingDataSourceFactory(context: Context) : DataSource.Factory {
    private val baseDataSourceFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return LoggingDataSource(baseDataSourceFactory.createDataSource())
    }
}

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var p2pServer: P2PML

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(true)
        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

        val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8"
        lifecycleScope.launch {
            p2pServer = P2PML(this@MainActivity, lifecycleScope)
            val manifest =
                p2pServer.getServerManifestUrl(streamUrl)

            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory).createMediaSource(
                MediaItem.fromUri(manifest)
            )

            player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }

            fun getCurrentPositionAndSpeed(): Pair<Float, Float> {
                return Pair(player.currentPosition / 1000f, player.playbackParameters.speed)
            }
            p2pServer.setUpPlaybackInfoCallback(::getCurrentPositionAndSpeed)

            setContent {
                ExoPlayerScreen(player = player, videoTitle = "Test Stream")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        p2pServer.stopServer()
    }
}

@Composable
fun ExoPlayerScreen(player: ExoPlayer, videoTitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = videoTitle,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineMedium
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                }
            }
        )
    }
}
