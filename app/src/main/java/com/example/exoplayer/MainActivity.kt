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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.p2pml.P2PML
import kotlinx.coroutines.launch


@UnstableApi
class LoggingDataSource(private val wrappedDataSource: DataSource) : DataSource {
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

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            wrappedDataSource.read(buffer, offset, length)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error reading data source: ${e.message}", e)
            throw e
        }
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
class LoggingDataSourceFactory(context: Context) : DataSource.Factory {
    private val baseDataSourceFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        return LoggingDataSource(baseDataSourceFactory.createDataSource())
    }
}

@UnstableApi
class MainActivity : ComponentActivity() {
    private var p2pServer = P2PML()
    private lateinit var player: ExoPlayer


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(true)
        //val customStream = "http://192.168.1.111:8000/hls/stream.m3u8"
        //val streamUrl = "https://video-weaver.waw02.hls.live-video.net/v1/playlist/CrcFLQHweNhMdaIHaP2Z_OxBWlITFHLnLpydpyhQE0B8URdt2hKrl9C_o94IAG3trj47_AxhBFwo4ILUdt__yLnlVaTu4m9bgZbP_-RUOhX6huPei6e9PuHGM5VB0QjzyH67vUblWu7J5peaLO0D7uO8RwI9lTqgbxNIrkzTCPg8v6yLWPonktjZ-mv_gG1hx_zcwPZtKyZ58_ZBnB3_6bTjPXwGRAtr3Wnh1geBAbRHwPQMuYXA1XYw5Yqj3lwd4cPZNduuDcFkVnsUfSLqSGVmQU95i-ciOU9Rz-nUUffo5woMgxdEjVc8aZbvdgCErrCzGgxedb6_4uTA85JlzNM-d9GZvqhatSOZwBVMyetYFTIcAl9PRaQPmYCzHIJa15hADhCy2T0mnDIHzbrfdKguZLc_sPX6yduST1hHMJkihjkOdekQIFUtA5AigcIQiAtupbVSMMvb1-CLOK2qyX4HV5aX2SY7c_fhFcy3VTcMSaRj5jQzOGIYRbUkwIaLoexHJ4SgeHnhvo1rhdUV2djX4iKYFgn20Mf3Yh3Bg5Dwj2o7bBds34L5bJPVEKuqGqdK-hcIAUbE7pXIWOt2IiJukdXsHnGk8guCWmBOPddGF78ycfXQrTfYmuEL_kazNChwHvYLYFYJTOvF50qwA1J0BKdPMoHKIMcA59YJZ9HjgX32BMV1EHBHA9zdou7MRFzmdYS1g38M4zdJ7UtVH33s9mpFdjRxk7549bG3iwMmHprg67B2ShxC5ctlxtwrCqcQU7uZ7ec1soXBYzh_x9VLDyKbWGCPcJvCFT_LSq6WHn8pOxMCsyQcBMkcx9gAm3g-nrgrNqCp9a_9jafq-IWJ6y07n2rSs0vYqO7MZBxIr9yDb5QP7fDs5ASDyTIBqRWSNxLHKmOCtvEeoIJZLXUzp4halzaeKG0aDFugHL8phVbBiOwCsCABKglldS13ZXN0LTIw4Qo.m3u8"
        //val streamUrl = "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8"

        //var streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        val streamUrl =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

        //val streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        //val streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8"
        lifecycleScope.launch {
            p2pServer.initialize(this@MainActivity, lifecycleScope)

            val manifest =
                p2pServer.getServerManifestUrl(streamUrl)

            val loggingDataSourceFactory = LoggingDataSourceFactory(this@MainActivity)
            val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory).createMediaSource(
                MediaItem.fromUri(manifest)
            )

            val trackSelector = DefaultTrackSelector(this@MainActivity).apply {
                val parameters = buildUponParameters()
                    .setMaxVideoSizeSd()
                    .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                    .setForceHighestSupportedBitrate(true)
                    .build()
                setParameters(parameters)
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000, // Minimum buffer before playback can start or resume (in milliseconds)
                    60000, // Maximum buffer to be retained in memory (in milliseconds)
                    1500,  // Buffer required before starting playback (in milliseconds)
                    3000   // Buffer required after a rebuffer (in milliseconds)
                )
                .build()
            //val loadControl = DefaultLoadControl.Builder().setLi


            /*player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }*/
            player = ExoPlayer.Builder(this@MainActivity)
                //.setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }

            p2pServer.setExoPlayer(player)

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
