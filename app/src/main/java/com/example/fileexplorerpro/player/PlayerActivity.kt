package com.example.fileexplorerpro.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

data class TrackChoice(val groupIndex: Int, val trackIndex: Int, val label: String)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    var exoPlayer: ExoPlayer? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = intent.data
        if (uri == null) { finish(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerScreen(uri = uri, onExit = { finish() }, onCreatePip = { enterPip() })
                }
            }
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPipMode)
        if (isInPipMode) exoPlayer?.playWhenReady = true
    }

    override fun onDestroy() { exoPlayer?.release(); exoPlayer = null; super.onDestroy() }
}

@SuppressLint("UnsafeOptInUsageError")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(uri: Uri?, onExit: () -> Unit, onCreatePip: () -> Unit) {
    val ctx = LocalContext.current
    val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var controlsVisible by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var locked by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var gestureType by remember { mutableStateOf("") }
    var gestureStarted by remember { mutableStateOf(false) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    var currentBrightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    val trackSelector = remember { DefaultTrackSelector(ctx) }
    val exo = remember {
        ExoPlayer.Builder(ctx).setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true).build().apply {
                uri?.let { setMediaItem(MediaItem.fromUri(it)); prepare(); playWhenReady = true }
            }
    }
    LaunchedEffect(Unit) {
        (ctx as? PlayerActivity)?.exoPlayer = exo
        while (true) { pos = exo.currentPosition; dur = exo.duration.coerceAtLeast(0); delay(250) }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && !locked) { delay(4000); controlsVisible = false }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener); exo.release() }
    }

    Box(Modifier.fillMaxSize()
        .pointerInput(locked) {
            if (locked) return@pointerInput
            detectTapGestures(
                onTap = { controlsVisible = !controlsVisible },
                onDoubleTap = { e ->
                    val half = size.width / 2
                    if (e.x < half) exo.seekTo((exo.currentPosition - 10000).coerceAtLeast(0))
                    else exo.seekTo((exo.currentPosition + 10000).coerceAtMost(dur))
                }
            )
        }
        .pointerInput(locked) {
            if (locked) return@pointerInput
            var startX = 0f
            detectDragGestures(
                onDragStart = { o -> startX = o.x; gestureStarted = true },
                onDragEnd = {
                    gestureStarted = false
                    if (gestureType == "seek") exo.seekTo(seekTarget.coerceIn(0, dur))
                    gestureType = ""
                },
                onDragCancel = { gestureStarted = false; gestureType = "" },
                onDrag = { change, drag ->
                    change.consume(); if (!gestureStarted) return@detectDragGestures
                    if (gestureType.isEmpty()) {
                        if (abs(drag.y) > abs(drag.x) && abs(drag.y) > 5)
                            gestureType = if (startX < size.width / 2f) "brightness" else "volume"
                        else if (abs(drag.x) > abs(drag.y) && abs(drag.x) > 5) gestureType = "seek"
                    }
                    when (gestureType) {
                        "brightness" -> {
                            currentBrightness = (currentBrightness - drag.y / size.height * 0.5f)
                                .coerceIn(0.01f, 1f)
                            (ctx as? ComponentActivity)?.window?.let { w ->
                                w.attributes = w.attributes.apply { screenBrightness = currentBrightness }
                            }
                        }
                        "volume" -> {
                            val delta = (-drag.y / size.height * maxVolume).roundToInt()
                            volume = (volume + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            gestureValue = volume.toFloat()
                        }
                        "seek" -> {
                            val seekDelta = (drag.x / size.width) * dur
                            seekTarget = (exo.currentPosition + seekDelta.toLong()).coerceIn(0, dur)
                            pos = seekTarget
                        }
                    }
                }
            )
        }
    ) {
        AndroidView(factory = { PlayerView(it).apply {
            player = exo; useController = false; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize())

        if (gestureStarted && gestureType.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when (gestureType) {
                        "brightness" -> Icons.Default.Brightness6
                        "volume" -> if (gestureValue > maxVolume / 2)
                            Icons.Default.VolumeUp else Icons.Default.VolumeDown
                        "seek" -> Icons.Default.Forward
                        else -> Icons.Default.Info
                    }
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    val txt = when (gestureType) {
                        "brightness" -> "السطوع: " + (currentBrightness * 100).roundToInt() + "%"
                        "volume" -> "الصوت: " + (gestureValue * 100 / maxVolume).roundToInt() + "%"
                        "seek" -> fmt(seekTarget) + " / " + fmt(dur)
                        else -> ""
                    }
                    Text(txt, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (controlsVisible) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onExit) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text(uri?.lastPathSegment ?: "وسائط", color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 16.sp)
                    if (!locked) {
                        IconButton({ showSpeedMenu = true }) {
                            Text("${speed}x", color = Color.White, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        IconButton({ showTrackMenu = true }) {
                            Icon(Icons.Default.Audiotrack, null, tint = Color.White)
                        }
                        IconButton({ showSubtitleMenu = true }) {
                            Icon(Icons.Default.Subtitles, null, tint = Color.White)
                        }
                        IconButton(onCreatePip) {
                            Icon(Icons.Default.PictureInPictureAlt, null, tint = Color.White)
                        }
                    }
                    IconButton({ locked = !locked }) {
                        Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            null, tint = Color.White)
                    }
                }
                if (!locked) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ exo.seekTo((exo.currentPosition - 10000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White,
                                modifier = Modifier.size(40.dp))
                        }
                        Box(Modifier.size(64.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            IconButton({ if (exo.isPlaying) exo.pause() else exo.play() }) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(44.dp))
                            }
                        }
                        IconButton({ exo.seekTo((exo.currentPosition + 10000).coerceAtMost(dur)) }) {
                            Icon(Icons.Default.Forward10, null, tint = Color.White,
                                modifier = Modifier.size(40.dp))
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Slider(value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { exo.seekTo((it * dur).toLong()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = Color.White,
                                activeTrackColor = Color(0xFF2196F3),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)))
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmt(pos), color = Color.White, fontSize = 12.sp)
                            Text(fmt(dur), color = Color.White, fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton({ locked = false; controlsVisible = true }) {
                            Icon(Icons.Default.Lock, null,
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSpeedMenu) {
        AlertDialog(onDismissRequest = { showSpeedMenu = false },
            title = { Text("سرعة التشغيل") },
            text = { Column {
                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                    TextButton({ speed = s; exo.playbackParameters = PlaybackParameters(s)
                        showSpeedMenu = false }) {
                        Text("${s}x", color = if (s == speed)
                            MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            } },
            confirmButton = {})
    }

    if (showTrackMenu) {
        val audioTracks = remember { mutableStateListOf<TrackChoice>() }
        LaunchedEffect(exo, showTrackMenu) {
            audioTracks.clear()
            delay(300)
            exo.currentTracks.groups.forEachIndexed { gi, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (ti in 0 until group.length) {
                        val f = group.getTrackFormat(ti)
                        audioTracks.add(TrackChoice(gi, ti,
                            f.label ?: f.language ?: "مسار صوتي ${audioTracks.size + 1}"))
                    }
                }
            }
        }
        AlertDialog(onDismissRequest = { showTrackMenu = false },
            title = { Text("المسارات الصوتية") },
            text = { Column {
                if (audioTracks.isEmpty()) {
                    Text("لا توجد مسارات صوتية متعددة")
                } else {
                    for (i in audioTracks.indices) {
                        val c = audioTracks[i]
                        TextButton(onClick = {
                            val group = exo.currentTracks.groups.getOrNull(c.groupIndex)
                            if (group != null) {
                                val override = TrackSelectionOverride(
                                    group.mediaTrackGroup, listOf(c.trackIndex))
                                exo.trackSelectionParameters =
                                    exo.trackSelectionParameters.buildUpon()
                                        .addOverride(override).build()
                            }
                            showTrackMenu = false
                        }) { Text(c.label) }
                    }
                }
            } },
            confirmButton = {})
    }

    if (showSubtitleMenu) {
        val subTracks = remember { mutableStateListOf<TrackChoice>() }
        var subEnabled by remember { mutableStateOf(true) }
        LaunchedEffect(exo, showSubtitleMenu) {
            subTracks.clear()
            delay(300)
            exo.currentTracks.groups.forEachIndexed { gi, group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (ti in 0 until group.length) {
                        val f = group.getTrackFormat(ti)
                        subTracks.add(TrackChoice(gi, ti,
                            f.label ?: f.language ?: "ترجمة ${subTracks.size + 1}"))
                    }
                }
            }
        }
        AlertDialog(onDismissRequest = { showSubtitleMenu = false },
            title = { Text("الترجمات") },
            text = { Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("تفعيل الترجمة", modifier = Modifier.weight(1f))
                    Switch(checked = subEnabled, onCheckedChange = { enabled ->
                        subEnabled = enabled
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled).build()
                    })
                }
                if (subTracks.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    for (i in subTracks.indices) {
                        val c = subTracks[i]
                        TextButton(onClick = {
                            val group = exo.currentTracks.groups.getOrNull(c.groupIndex)
                            if (group != null) {
                                val override = TrackSelectionOverride(
                                    group.mediaTrackGroup, listOf(c.trackIndex))
                                exo.trackSelectionParameters =
                                    exo.trackSelectionParameters.buildUpon()
                                        .addOverride(override).build()
                            }
                            subEnabled = true
                            showSubtitleMenu = false
                        }) { Text(c.label) }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد ترجمات مدمجة",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } },
            confirmButton = {})
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
