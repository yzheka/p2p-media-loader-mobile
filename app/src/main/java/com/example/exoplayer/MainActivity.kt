package com.example.exoplayer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.p2pml.P2PMediaLoader
import kotlinx.coroutines.launch

@UnstableApi
class LoggingDataSource(
    private val wrappedDataSource: DataSource,
) : DataSource {
    @OptIn(UnstableApi::class)
    override fun open(dataSpec: DataSpec): Long {
        Log.d("HLSSegmentLogger", "Requesting: ${dataSpec.uri}")
        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error opening data source: ${e.message}", e)
            throw e
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        try {
            wrappedDataSource.read(buffer, offset, length)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error reading data source: ${e.message}", e)
            throw e
        }

    override fun addTransferListener(transferListener: TransferListener) {
        wrappedDataSource.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = wrappedDataSource.uri

    override fun close() {
        try {
            wrappedDataSource.close()
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error closing data source: ${e.message}", e)
        }
    }
}

@UnstableApi
class LoggingDataSourceFactory(
    context: Context,
) : DataSource.Factory {
    private val httpDataSourceFactory =
        DefaultHttpDataSource
            .Factory()
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)

    private val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    override fun createDataSource(): DataSource = LoggingDataSource(baseDataSourceFactory.createDataSource())
}

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var p2pml: P2PMediaLoader
    private lateinit var player: ExoPlayer

    private val loadingState = mutableStateOf(true)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(true)

        lifecycleScope.launch {
            p2pml =
                P2PMediaLoader
                    .Builder()
                    .setCoreConfig("{\"swarmId\":\"TEST_KOTLIN\"}")
                    .setServerPort(8081)
                    .build()
                    .apply {
                        start(this@MainActivity, lifecycleScope)
                    }

            val manifest =
                p2pml.getManifestUrl(Streams.HLS_BIG_BUCK_BUNNY_QUALITY_4)

            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource =
                HlsMediaSource
                    .Factory(loggingDataSourceFactory)
                    .createMediaSource(
                        MediaItem.fromUri(manifest),
                    )

            player =
                ExoPlayer
                    .Builder(this@MainActivity)
                    .build()
                    .apply {
                        setMediaSource(mediaSource)
                        prepare()
                        playWhenReady = true
                        addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState != Player.STATE_READY) return
                                    loadingState.value = false
                                }
                            },
                        )
                        p2pml.attachPlayer(this)
                    }

            setContent {
                ExoPlayerScreen(player = player, videoTitle = "Test Stream", loadingState.value)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        p2pml.applyDynamicConfig("{ \"isP2PDisabled\": true }")
    }

    override fun onRestart() {
        super.onRestart()
        p2pml.applyDynamicConfig("{ \"isP2PDisabled\": false }")
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        p2pml.stop()
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun ExoPlayerScreen(
    player: ExoPlayer,
    videoTitle: String,
    isLoading: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = videoTitle,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineMedium,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                    }
                },
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
