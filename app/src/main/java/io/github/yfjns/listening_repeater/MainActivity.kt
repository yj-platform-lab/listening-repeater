package io.github.yfjns.listening_repeater

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.yfjns.listening_repeater.ui.theme.ListeningrepeaterTheme

val android.content.Context.dataStore by preferencesDataStore(name = "settings")

val SAVED_FOLDER_URIS = stringSetPreferencesKey("saved_folder_uris")
class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val controllerFuture = MediaController.Builder(
            this,
            sessionToken
        ).buildAsync()

        controllerFuture.addListener(
            {
                controller = controllerFuture.get()

                setContent {
                    ListeningrepeaterTheme {
                        LibraryScreen(controller!!)
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        controller?.release()
        controller = null
    }
}
data class AudioFile(
    val name: String,
    val uri: Uri
)

data class AudioFolder(
    val name: String,
    val uri: Uri,
    val files: List<AudioFile>
)

enum class Screen {
    LIBRARY,
    PLAYER
}
@Composable
fun LibraryScreen(player: MediaController) {
    var folders by remember {
        mutableStateOf<List<AudioFolder>>(emptyList())
    }
    var expandedFolderUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var currentScreen by remember {
        mutableStateOf(Screen.LIBRARY)
    }

    var selectedAudioName by remember {
        mutableStateOf("Title")
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val savedUris =
            context.dataStore.data.first()[SAVED_FOLDER_URIS] ?: emptySet()

        folders = savedUris.map { uriText ->
            val uri = Uri.parse(uriText)
            createAudioFolder(context, uri)
        }
    }


    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val newFolder = createAudioFolder(context, uri)

            folders = folders + newFolder

            scope.launch {
                context.dataStore.edit { settings ->
                    settings[SAVED_FOLDER_URIS] =
                        folders.map { it.uri.toString() }.toSet()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        if (currentScreen == Screen.LIBRARY) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library",
                        color = Color.White,
                        fontSize = 28.sp
                    )

                    IconButton(
                        onClick = {
                            folderPickerLauncher.launch(null)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_create_new_folder),
                            contentDescription = "Add folder",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (folders.isEmpty()) {
                    Text(
                        text = "No folder selected",
                        color = Color.LightGray,
                        fontSize = 18.sp
                    )
                } else {
                    folders.forEach { folder ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clickable {
                                    expandedFolderUri =
                                        if (expandedFolderUri == folder.uri) {
                                            null
                                        } else {
                                            folder.uri
                                        }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )

                            Spacer(modifier = Modifier.width(24.dp))

                            Text(
                                text = folder.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    folders = folders.filter { it.uri != folder.uri }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }

                        if (expandedFolderUri == folder.uri) {
                            folder.files.forEach { audio ->
                                Text(
                                    text = audio.name,
                                    color = Color.LightGray,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedAudioName = audio.name
                                            player.stop()
                                            player.clearMediaItems()
                                            player.setMediaItem(
                                                MediaItem.fromUri(audio.uri)
                                            )
                                            player.prepare()
                                            player.playWhenReady = true
                                            currentScreen = Screen.PLAYER
                                        }
                                        .padding(
                                            start = 60.dp,
                                            top = 6.dp,
                                            bottom = 6.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            PlayerScreen(
                player = player,
                title = selectedAudioName,
                onBack = {
                    currentScreen = Screen.LIBRARY
                }
            )
        }
    }
}

@Composable
fun PlayerScreen(
    player: MediaController,
    title: String,
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onBack
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val duration = player.duration.coerceAtLeast(0L)

            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (duration > 0L) {
                Slider(
                    value = currentPosition.coerceAtMost(duration).toFloat(),
                    onValueChange = { value ->
                        player.seekTo(value.toLong())
                    },
                    valueRange = 0f..duration.toFloat()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newPosition =
                            (player.currentPosition - 10_000).coerceAtLeast(0)
                        player.seekTo(newPosition)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Back 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.play()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = "Play or pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val newPosition = player.currentPosition + 10_000
                        player.seekTo(newPosition)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val speeds = listOf(0.75f, 1.0f, 1.2f, 1.5f, 1.7f)
            var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                speeds.forEach { speed ->
                    IconButton(
                        onClick = {
                            selectedSpeed = speed
                            player.setPlaybackSpeed(speed)
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = when (speed) {
                                    0.75f -> R.drawable.ic_speed_0_75x
                                    1.0f -> R.drawable.ic_speed_1x
                                    1.2f -> R.drawable.ic_speed_1_2x
                                    1.5f -> R.drawable.ic_speed_1_5x
                                    else -> R.drawable.ic_speed_1_7x
                                }
                            ),
                            contentDescription = "Speed",
                            tint =
                                if (selectedSpeed == speed)
                                    Color.Green
                                else
                                    Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d".format(minutes, seconds)
}

fun getFolderName(uri: Uri): String {
    val text = uri.toString()
    val folderName = text.substringAfterLast("%3A")
    return folderName.ifBlank { "Selected folder" }
}

fun createAudioFolder(
    context: android.content.Context,
    uri: Uri
): AudioFolder {
    val folder = DocumentFile.fromTreeUri(context, uri)

    val files = folder
        ?.listFiles()
        ?.filter { file ->
            file.isFile &&
                    file.name?.endsWith(".mp3", ignoreCase = true) == true
        }
        ?.map { file ->
            AudioFile(
                name = file.name ?: "Unknown",
                uri = file.uri
            )
        }
        ?: emptyList()

    return AudioFolder(
        name = getFolderName(uri),
        uri = uri,
        files = files
    )
}