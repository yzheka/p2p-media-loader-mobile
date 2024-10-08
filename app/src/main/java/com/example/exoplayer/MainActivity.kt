package com.example.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebMessagePort
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import com.example.p2pml.P2PMLServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private lateinit var webView: WebView
    private lateinit var webMessagePort: WebMessagePort

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        lifecycleScope.launch {
            val p2pServer = P2PMLServer()

            p2pServer.startServer()
            //val manifest = p2pServer.getServerManifestUrl("https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8")
            val manifest = p2pServer.getServerManifestUrl("https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8")
            //val manifest = p2pServer.getServerManifestUrl("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory).createMediaSource(
                MediaItem.fromUri(manifest)
            )

            player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        }

        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        player = this@MainActivity.player
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}


//        player.addListener(
//            object : Player.Listener {
//                @OptIn(UnstableApi::class)
//                override fun onTimelineChanged(timeline: Timeline, @Player.TimelineChangeReason reason: Int) {
//                    val manifest = player.currentManifest
//                    if (manifest is HlsManifest) {
//                        manifest.mediaPlaylist.let { playlist ->
//                            Log.d("=HLS_MANIFEST", "Media Playlist Duration: ${playlist.durationUs}")
//                            Log.d("=HLS_MANIFEST", "Media Playlist Start Time: ${playlist.startTimeUs}")
//                            Log.d("=HLS_MANIFEST", "Media Playlist End Time: ${playlist.endTimeUs}")
//                            // You can log other specific properties or details you are interested in
//                        }
//                    }
//
//                }
//            }
//        )
//        Handler(Looper.getMainLooper()).postDelayed({
//            val currentPos = player.currentPosition
//            Log.d("=CURRENT_POSITION", "Current Position: $currentPos")
//            val currentMediaItem = player.currentMediaItem
//
//            val tracks = player.currentTracks
//            val trackCount = tracks.groups.size
//            Log.d("=TRACK_COUNT", "Track Count: $trackCount")
//        }, 5000)