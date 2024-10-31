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

        //val streamUrl = "https://video-weaver.waw02.hls.live-video.net/v1/playlist/CrcFxsz4bdOgG-wDiZH3ER2CihuJZwtlbR2MrTk18HpIe3GwKGjqTk-OSwEtivZNm5g3Uoo6jyKdR4hbKtVdoki9zSto0DdwgffcA-Q-h7dvU-GLrK6rbOqLEy0U-ItDl0NF-bAdXaWoskhuxAIFVi8wd8Y0vYyTMt3EKW_S9VrlBoaHcenmpihSuXjsKYXm2GpU6EqXwdVHiENHl9efgPULPX-8-9dJ7jIxtg-Kuo7xZ20aCTlt-s3G3tfZ0ITZ3Z96PhfjLdXTu1YPQmLO5VEPyG7WBSprj5mYXRcV-_BoMxpLaRRrG5_5CfQkzoDueURXI30GM0NFC373hhCYdzQCWfrpq5Eu3QsdlbCs6_XyfAnjhNCFQsme_RYIWYiGyeR4nYRWKv9C66DB1YwrTLQ8_tR_zor11AZgfjmz5Sqar5i_GLS6VZs-Z0lwsAI-tnVKvzLNsIKJ-NPN1_RxvjKyYrN5aCUdL9_bzKHq2hQukAwlUyyfbUDxh1GAA6oKmvXlOCYfdc54bX3ph-8JJG-aWaa6YXaQGamgskaTIngwAxzf-yfo8pT8ugngrO33f8APWJQjAQfTC22FLpx-XdAV5usU67gG3kN1G7BHCHmGzUUfS0di_6hUFMxh9uU36vvdscPOikU9GABGTHCBdnfqh4LGXXOCUdZVf-i9-LLyV34Q4pmlqRwmHZJTgN3aoo1dlIpDLdM2zJLTPP59470FEsxVnx1OJK8IcWUfR_dqXeFOR-s31DIoHBXYAXTs4T87ActhWPqBGJbGU0BgWpEaX6h_W0AhV4UDSbX37bmLi2UI3Yy6T-1Tdzu6of0Un6DUOCG1BgjmoTjiMyvCuGDHPTaewRlE1f9tA-K5i5QUudKTR0lGomsWMyqImGoanRVs5679J6gaaYxWP84tU6M1CN1wIOJgJbQaDExCVzqPB70bTf9u-CABKglldS13ZXN0LTIw3wo.m3u8"
        //val streamUrl = "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8"

        //var streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

        val streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
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
