package io.github.yfjns.listening_repeater

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.github.yfjns.listening_repeater.ui.theme.ListeningrepeaterTheme

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()

        val uri = Uri.parse("android.resource://$packageName/${R.raw.menuettm}")
        val mediaItem = MediaItem.fromUri(uri)

        player.setMediaItem(mediaItem)
        player.prepare()

        setContent {
            ListeningrepeaterTheme {
                PlayerScreen(player)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

@Composable
fun PlayerScreen(player: ExoPlayer) {
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Listening Repeater")

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.play()
                    isPlaying = true
                }
            }
        ) {
            Text(if (isPlaying) "Pause" else "Play")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val newPosition = (player.currentPosition - 10_000).coerceAtLeast(0)
                player.seekTo(newPosition)
            }
        ) {
            Text("Back 10 sec")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val newPosition = player.currentPosition + 10_000
                player.seekTo(newPosition)
            }
        ) {
            Text("Forward 10 sec")
        }
    }
}