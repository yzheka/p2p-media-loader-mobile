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
import androidx.media3.exoplayer.DefaultLoadControl
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

        val streamUrl = "https://video-weaver.waw02.hls.live-video.net/v1/playlist/CrgFccpDUdSb0-36OTAxgDsPTsZG9pmQjJapDgX2sYoQNSoQmR7-9gtVVweHUZY2XAkRwg9Ra2A0A0Ja8Ev0bLpb_4uygvIodu4mGyB0TwgvNeH6uHE7MAr8QdmruHK28fvgjNr90YlTXgSS-RGUO7ld0NIZw8NajjRkO9vp51qKliqlGJtllCP_icLVxnkrLQ50sqXCnzyMYKkzaiLNdcNKo3ADwzwSm_G-1GMj5bbkAC1Pj6o2fVg5RZgngghZOvaLGttF7i0Cl4U7XrgL7zG1j8_430WyCjven91BfSIR_0Gdp8CB7WbRKeIj6_kWB9rcUvDv4zThWfEZ0XKdCHdrfkoqbOI7rN7MDA_8FSfxJBtgLAd6qNbS2U_v142yT-V8ZAZ-ntqpS4-6I6th_GL1lsW7bPh4fPTkpVmCe__V2a4O_XWHfANYi3cgUZvBKDzes4TbD4f5hA6ijAcwjGiA93NHY7gKTPrI5xSOzBITuUtE2oWYPxVQ894n-76Dgp7mtzdo_BDbuEe_u3I0By-ZgKXWja6k_d1tVPenl9gYv8kjXlFoFgHhjTaSZg_ayCtj08tX_OWYRlKJKjCpBDA3MXdUGuHwBo8F0QULuckxBNBcJj-wpWWysWJaX-fu2oKu_N8oPmBa2IuIZE1dG0vb7WPxyR2BqVC0B_0Y4aabmCDTxcTF5Q_FxeibHok3T6t4haLpvpvuElR_Lr4q0kAvu85X3u_5HIWgJOsXOXtP6Lwf61pAXAukeMUheZZYuH4sEO4hhNUnC8UYr-3-C5fofbSxmyw9b_IiIkb5e8DRTEg0GAwiqZN0hxQaMzu8R0tw5NldyOPSrZ2oeavTwdSEMPn2XKn5FvJCuKPm58SlzbRyiJMdJxe98CqMNB-h_a4GuI2MnHJ0V_Rh53b6UW1va7AJ51NLPogiGgwcCUsFOx4OCuGEzlsgASoJZXUtd2VzdC0yMN0K.m3u8"
        //val streamUrl = "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8"

        //var streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

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


            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000, // Minimum buffer before playback can start or resume (in milliseconds)
                    60000, // Maximum buffer to be retained in memory (in milliseconds)
                    1500,  // Buffer required before starting playback (in milliseconds)
                    3000   // Buffer required after a rebuffer (in milliseconds)
                )
                .build()

            /*player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }*/
            val player = ExoPlayer.Builder(this@MainActivity)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }


            p2pServer.setExoPlayer(player)

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
