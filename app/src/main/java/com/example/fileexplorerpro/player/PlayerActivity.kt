package com.example.fileexplorerpro.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

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
                        }
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(uri: Uri, onExit: () -> Unit, onPip: () -> Unit) {
    val ctx = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var audioMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }

    DisposableEffect(uri) {
        val token = SessionToken(ctx, ComponentName(ctx, MediaPlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.setMediaItem(MediaItem.fromUri(uri))
                c.prepare()
                c.playWhenReady = true
                controller = c
            } catch (_: Exception) {
            }
        }, MoreExecutors.directExecutor())
        onDispose {
            future.addListener({
                try {
                    future.get().release()
                } catch (_: Exception) {
                }
            }, MoreExecutors.directExecutor())
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    val exo = controller
    DisposableEffect(exo) {
        if (exo == null) return@DisposableEffect onDispose { }
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) {
                playing = p
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    LaunchedEffect(exo) {
        val p = exo ?: return@LaunchedEffect
        while (true) {
            pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0)
            delay(if (p.isPlaying) 500 else 1000)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { showControls = !showControls },
                onDoubleTap = {
                    val p = controller ?: return@detectTapGestures
                    if (p.isPlaying) p.pause() else p.play()
                }
            )
        }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { it.player = controller },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onExit) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
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
                                onClick = {
                                    speed = s
                                    controller?.setPlaybackSpeed(s)
                                    sm = false
                                }
                            )
                        }
                    }
                    if (audioTracks.size > 1) {
                        IconButton({ audioMenu = true }) {
                            Icon(Icons.Default.Audiotrack, "مسار الصوت", tint = Color.White)
                        }
                        DropdownMenu(audioMenu, { audioMenu = false }) {
                            audioTracks.forEach { (groupIndex, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        val p = controller
                                        val group = p?.currentTracks?.groups?.getOrNull(groupIndex)
                                        if (p != null && group != null) {
                                            p.trackSelectionParameters =
                                                p.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(group.mediaTrackGroup, 0)
                                                    )
                                                    .build()
                                        }
                                        audioMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onPip) {
                        Icon(Icons.Default.PictureInPicture, "نافذة ضمن نافذة", tint = Color.White)
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton({
                        controller?.let {
                            it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
                        }
                    }) {
                        Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    IconButton({
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    }) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton({
                        controller?.seekTo((controller!!.currentPosition + 10000).coerceAtMost(dur))
                    }) {
                        Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Slider(
                        value = if (dur > 0) pos.toFloat() / dur else 0f,
                        onValueChange = { controller?.seekTo((it * dur).toLong()) },
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
