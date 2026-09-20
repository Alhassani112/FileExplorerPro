package com.example.fileexplorerpro.player

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Accent = Color(0xFF42A5F5)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    uri: Uri,
    inPip: Boolean,
    onExit: () -> Unit,
    onPip: () -> Unit
) {
    val ctx = LocalContext.current
    val resume = remember { PlayerResumeStore(ctx) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var textTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var subsOn by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableFloatStateOf(currentBrightness(ctx)) }
    var volumeFrac by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat())
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(uri) {
        val token = SessionToken(ctx, ComponentName(ctx, MediaPlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.setMediaItem(SidecarSubtitles.mediaItem(uri))
                c.prepare()
                val saved = resume.get(uri)
                if (saved > 5_000L) c.seekTo(saved)
                c.playWhenReady = true
                controller = c
            } catch (e: Exception) {
                error = e.message ?: "تعذر فتح المشغّل"
            }
        }, MoreExecutors.directExecutor())
        onDispose {
            try {
                val c = if (future.isDone) future.get() else null
                c?.let { resume.save(uri, it.currentPosition) }
                c?.release()
            } catch (_: Exception) {
            }
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

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_ENDED) playing = false
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.localizedMessage ?: "خطأ في التشغيل"
                buffering = false
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = tracks.named(C.TRACK_TYPE_AUDIO)
                textTracks = tracks.named(C.TRACK_TYPE_TEXT)
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    LaunchedEffect(exo, uri) {
        val p = exo ?: return@LaunchedEffect
        while (true) {
            pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0)
            buffered = p.bufferedPosition.coerceAtLeast(0)
            if (p.currentPosition % 8_000 < 600) resume.save(uri, p.currentPosition)
            delay(if (p.isPlaying) 250 else 800)
        }
    }

    LaunchedEffect(showControls, locked, playing) {
        if (showControls && !locked && playing) {
            delay(4_000)
            showControls = false
        }
    }

    LaunchedEffect(overlay) {
        if (overlay != null) {
            delay(700)
            overlay = null
        }
    }

    fun seekBy(delta: Long) {
        val p = controller ?: return
        val d = p.duration.coerceAtLeast(0)
        val next = (p.currentPosition + delta).coerceIn(0, if (d > 0) d else Long.MAX_VALUE)
        p.seekTo(next)
        overlay = if (delta < 0) "−10 ث" else "+10 ث"
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    playerViewRef = this
                }
            },
            update = {
                it.player = controller
                it.resizeMode = resize
                it.useController = false
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!inPip && !locked) {
            Box(
                Modifier.fillMaxSize().pointerInput(controller, dur) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            when {
                                offset.x < third -> seekBy(-10_000)
                                offset.x > third * 2 -> seekBy(10_000)
                                else -> controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            }
                        }
                    )
                }.pointerInput(controller, locked) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val x = change.position.x
                        if (abs(drag.y) >= abs(drag.x)) {
                            val delta = -drag.y / h
                            if (x < w / 2f) {
                                brightness = (brightness + delta).coerceIn(0.05f, 1f)
                                setBrightness(ctx, brightness)
                                overlay = "السطوع ${(brightness * 100).toInt()}%"
                            } else {
                                volumeFrac = (volumeFrac + delta).coerceIn(0f, 1f)
                                audio.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (volumeFrac * maxVol).toInt(),
                                    0
                                )
                                overlay = "الصوت ${(volumeFrac * 100).toInt()}%"
                            }
                        } else if (dur > 0) {
                            val p = controller ?: return@detectDragGestures
                            val skip = (drag.x / w * dur * 0.4f).toLong()
                            val next = (p.currentPosition + skip).coerceIn(0, dur)
                            p.seekTo(next)
                            overlay = formatPlayerTime(next)
                        }
                    }
                }
            )
        }

        if (locked && !inPip) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { showControls = true }
                }
            )
        }

        if (buffering && error == null) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Accent
            )
        }

        overlay?.let { msg ->
            GestureChip(
                text = msg,
                icon = when {
                    msg.startsWith("السطوع") -> Icons.Default.BrightnessHigh
                    msg.startsWith("الصوت") -> Icons.Default.VolumeUp
                    else -> Icons.Default.Forward10
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        error?.let { err ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(err, color = Color.White)
                TextButton(onClick = {
                    error = null
                    controller?.prepare()
                    controller?.play()
                }) { Text("إعادة المحاولة", color = Accent) }
            }
        }

        AnimatedVisibility(
            visible = !inPip && (showControls || locked),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (locked) 0.15f else 0.45f))) {
                if (locked) {
                    IconButton(
                        onClick = { locked = false; showControls = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.5f))
                    ) {
                        Icon(Icons.Default.LockOpen, "فتح القفل", tint = Color.White)
                    }
                } else {
                    Controls(
                        title = uri.lastPathSegment ?: "وسائط",
                        playing = playing,
                        pos = pos,
                        dur = dur,
                        buffered = buffered,
                        speed = speed,
                        audioTracks = audioTracks,
                        textTracks = textTracks,
                        subsOn = subsOn,
                        audioMenu = audioMenu,
                        speedMenu = speedMenu,
                        onAudioMenu = { audioMenu = it },
                        onSpeedMenu = { speedMenu = it },
                        onClose = onExit,
                        onPip = onPip,
                        onLock = { locked = true; showControls = false },
                        onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                        onSeekBack = { seekBy(-10_000) },
                        onSeekFwd = { seekBy(10_000) },
                        onSeekFraction = { f ->
                            if (dur > 0) controller?.seekTo((f * dur).toLong())
                        },
                        onSpeed = { s ->
                            speed = s
                            controller?.setPlaybackSpeed(s)
                        },
                        onAudio = { idx -> selectTrack(controller, audioTracks, idx) },
                        onToggleSubs = {
                            val p = controller ?: return@Controls
                            subsOn = !subsOn
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn)
                                .build()
                        },
                        onResize = {
                            resize = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            playerViewRef?.resizeMode = resize
                            overlay = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ملء مع قص"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "تمديد"
                                else -> "ملاءمة"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Controls(
    title: String,
    playing: Boolean,
    pos: Long,
    dur: Long,
    buffered: Long,
    speed: Float,
    audioTracks: List<Pair<Int, String>>,
    textTracks: List<Pair<Int, String>>,
    subsOn: Boolean,
    audioMenu: Boolean,
    speedMenu: Boolean,
    onAudioMenu: (Boolean) -> Unit,
    onSpeedMenu: (Boolean) -> Unit,
    onClose: () -> Unit,
    onPip: () -> Unit,
    onLock: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onAudio: (Int) -> Unit,
    onToggleSubs: () -> Unit,
    onResize: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClose) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
            Text(
                title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onLock) { Icon(Icons.Default.Lock, "قفل", tint = Color.White) }
            IconButton({ onSpeedMenu(true) }) {
                Text("${speed}x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(speedMenu, { onSpeedMenu(false) }) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    DropdownMenuItem(
                        text = { Text("${s}x") },
                        onClick = { onSpeed(s); onSpeedMenu(false) }
                    )
                }
            }
            if (audioTracks.size > 1) {
                IconButton({ onAudioMenu(true) }) {
                    Icon(Icons.Default.Audiotrack, "الصوت", tint = Color.White)
                }
                DropdownMenu(audioMenu, { onAudioMenu(false) }) {
                    audioTracks.forEach { (idx, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onAudio(idx); onAudioMenu(false) }
                        )
                    }
                }
            }
            if (textTracks.isNotEmpty()) {
                IconButton(onToggleSubs) {
                    Icon(
                        if (subsOn) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        "ترجمة",
                        tint = Color.White
                    )
                }
            }
            IconButton(onResize) { Icon(Icons.Default.AspectRatio, "المقاس", tint = Color.White) }
            IconButton(onPip) { Icon(Icons.Default.PictureInPicture, "نافذة", tint = Color.White) }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onSeekBack, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.Replay10, "تراجع", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(16.dp))
            IconButton(
                onPlayPause,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.9f))
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "تشغيل",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onSeekFwd, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.Forward10, "تقديم", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Slider(
                value = frac,
                onValueChange = onSeekFraction,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color.White.copy(0.25f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(pos), color = Color.White, fontSize = 12.sp)
                Text(
                    if (buffered > 0 && dur > 0) "مخزن ${((buffered * 100) / dur).toInt()}%" else "",
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp
                )
                Text(formatPlayerTime(dur), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GestureChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(0.7f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun Tracks.named(type: Int): List<Pair<Int, String>> =
    groups.mapIndexedNotNull { index, g ->
        if (g.type != type || g.length == 0) return@mapIndexedNotNull null
        val f = g.getTrackFormat(0)
        val label = f.label ?: f.language ?: "مسار ${index + 1}"
        index to label
    }

@OptIn(UnstableApi::class)
private fun selectTrack(player: Player?, tracks: List<Pair<Int, String>>, groupIndex: Int) {
    val p = player ?: return
    val group = p.currentTracks.groups.getOrNull(groupIndex) ?: return
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .build()
}

private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun currentBrightness(ctx: Context): Float {
    val w = (ctx as? Activity)?.window ?: return 0.5f
    val b = w.attributes.screenBrightness
    return if (b in 0f..1f) b else 0.5f
}

private fun setBrightness(ctx: Context, value: Float) {
    val act = ctx as? Activity ?: return
    val lp = act.window.attributes
    lp.screenBrightness = value
    act.window.attributes = lp
}
