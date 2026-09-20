package com.example.fileexplorerpro.player

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = intent?.data
        if (uri == null) { finish(); return }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerScreen(
                        uri = uri,
                        onExit = { finish() },
                        onPip = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9)).build()
                                )
                            }
                        },
                        onReady = { player = it }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    uri: Uri,
    onExit: () -> Unit,
    onPip: () -> Unit,
    onReady: (ExoPlayer) -> Unit
) {
    val ctx = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }

    val exo = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(Unit) { onReady(exo) }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            exo.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            pos = exo.currentPosition
            dur = exo.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) { delay(4000); showControls = false }
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { if (exo.isPlaying) exo.pause() else exo.play() }
                )
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                // الشريط العلوي
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onExit) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Text(
                        uri.lastPathSegment ?: "وسائط",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    var sm by remember { mutableStateOf(false) }
                    IconButton({ sm = true }) {
                        Text("${speed}x", color = Color.White, fontSize = 13.sp)
                    }
                    DropdownMenu(sm, { sm = false }) {
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x") },
                                onClick = { speed = s; exo.setPlaybackSpeed(s); sm = false }
                            )
                        }
                    }
                    IconButton(onPip) {
                        Icon(Icons.Default.PictureInPicture, null, tint = Color.White)
                    }
                }

                // أزرار التحكم الوسطى
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton({ exo.seekTo((exo.currentPosition - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Default.Replay10, null, tint = Color.White,
                            modifier = Modifier.size(44.dp))
                    }
                    IconButton({ if (exo.isPlaying) exo.pause() else exo.play() }) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton({ exo.seekTo((exo.currentPosition + 10000).coerceAtMost(dur)) }) {
                        Icon(Icons.Default.Forward10, null, tint = Color.White,
                            modifier = Modifier.size(44.dp))
                    }
                }

                // الشريط السفلي
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) {
                    Slider(
                        value = if (dur > 0) pos.toFloat() / dur else 0f,
                        onValueChange = { exo.seekTo((it * dur).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF2196F3)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fmt(pos), color = Color.White, fontSize = 12.sp)
                        Text(fmt(dur), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
