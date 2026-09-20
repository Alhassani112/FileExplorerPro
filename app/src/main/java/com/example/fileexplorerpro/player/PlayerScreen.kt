package com.example.fileexplorerpro.player

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Gold = Color(0xFFFFC14A)
private val Glass = Color(0x33000000)
private val GlassStroke = Color(0x40FFFFFF)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    uri: Uri,
    inPip: Boolean,
    onExit: () -> Unit,
    onPip: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val resume = remember { PlayerResumeStore(ctx) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var hideGen by remember { mutableIntStateOf(0) }
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
    var brightness by remember { mutableFloatStateOf(currentBrightness(ctx)) }
    var volumeFrac by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat())
    }
    var sideHint by remember { mutableStateOf<SideHint?>(null) }
    var seekFlash by remember { mutableIntStateOf(0) } // -1 left 1 right 0 none
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    fun bumpControls() {
        showControls = true
        hideGen++
    }

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
                buffering = state == Player.STATE_BUFFERING
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
            if (!scrubbing) pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0)
            buffered = p.bufferedPosition.coerceAtLeast(0)
            delay(if (p.isPlaying && !showControls) 400 else 200)
        }
    }

    LaunchedEffect(hideGen, locked, playing, showControls) {
        if (showControls && !locked && playing) {
            delay(3_200)
            showControls = false
        }
    }

    LaunchedEffect(seekFlash) {
        if (seekFlash != 0) {
            delay(420)
            seekFlash = 0
        }
    }

    fun seekBy(delta: Long) {
        val p = controller ?: return
        val d = p.duration.coerceAtLeast(0)
        val next = (p.currentPosition + delta).coerceIn(0, if (d > 0) d else Long.MAX_VALUE)
        p.seekTo(next)
        pos = next
        seekFlash = if (delta < 0) -1 else 1
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val latestSeekBy = rememberUpdatedState(::seekBy)
    val latestController = rememberUpdatedState(controller)
    val latestDur = rememberUpdatedState(dur)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
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
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!inPip) {
            Box(
                Modifier.fillMaxSize().pointerInput(locked) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (locked) {
                            bumpControls()
                            return@awaitEachGesture
                        }
                        val start = down.position
                        val startTime = SystemClock.uptimeMillis()
                        var total = Offset.Zero
                        var dragging = false
                        var mode = 0
                        val slop = viewConfiguration.touchSlop * 1.6f
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val p0 = latestController.value
                        val startPos = p0?.currentPosition ?: 0L
                        val duration = latestDur.value
                        var lastSeekAt = 0L

                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                if (!dragging) {
                                    val dt = SystemClock.uptimeMillis() - startTime
                                    if (dt < 280) {
                                        // second tap handled below via double-tap window in detect — use position thirds
                                    }
                                } else if (mode == 3 && duration > 0) {
                                    val next = (startPos + (total.x / w * duration * 0.55f).toLong())
                                        .coerceIn(0, duration)
                                    p0?.seekTo(next)
                                    scrubbing = false
                                    pos = next
                                } else {
                                    sideHint = null
                                    scrubbing = false
                                }
                                break
                            }
                            val delta = ch.positionChange()
                            total += Offset(delta.x, delta.y)
                            if (!dragging && total.getDistance() > slop) {
                                dragging = true
                                mode = if (abs(total.x) > abs(total.y)) 3
                                else if (start.x < w * 0.45f) 1 else 2
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (dragging) {
                                ch.consume()
                                when (mode) {
                                    1 -> {
                                        brightness = (brightness - delta.y / h).coerceIn(0.05f, 1f)
                                        setBrightness(ctx, brightness)
                                        sideHint = SideHint.Brightness(brightness)
                                    }
                                    2 -> {
                                        volumeFrac = (volumeFrac - delta.y / h).coerceIn(0f, 1f)
                                        audio.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volumeFrac * maxVol).roundToInt(),
                                            0
                                        )
                                        sideHint = SideHint.Volume(volumeFrac)
                                    }
                                    3 -> if (duration > 0) {
                                        scrubbing = true
                                        val next = (startPos + (total.x / w * duration * 0.55f).toLong())
                                            .coerceIn(0, duration)
                                        scrubPos = next
                                        pos = next
                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastSeekAt > 90) {
                                            p0?.seekTo(next)
                                            lastSeekAt = now
                                        }
                                        sideHint = SideHint.Seek(next, duration)
                                    }
                                }
                            }
                        }
                    }
                }.pointerInput(locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val t0 = SystemClock.uptimeMillis()
                        val p0 = down.position
                        // wait up without much move
                        var moved = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if ((ch.position - p0).getDistance() > viewConfiguration.touchSlop * 2) {
                                moved = true
                            }
                            if (!ch.pressed) break
                        }
                        if (moved) return@awaitEachGesture
                        val wait = 260L - (SystemClock.uptimeMillis() - t0)
                        if (wait > 0) {
                            val second = withTimeoutOrNullCompat(wait) {
                                val d2 = awaitFirstDown()
                                d2
                            }
                            if (second != null) {
                                val third = size.width / 3f
                                when {
                                    second.position.x < third -> latestSeekBy.value(-10_000)
                                    second.position.x > third * 2 -> latestSeekBy.value(10_000)
                                    else -> latestController.value?.let {
                                        if (it.isPlaying) it.pause() else it.play()
                                    }
                                }
                                return@awaitEachGesture
                            }
                        }
                        showControls = !showControls
                        if (showControls) hideGen++
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = seekFlash != 0,
            enter = fadeIn(tween(80)) + scaleIn(tween(120)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Box(
                Modifier
                    .padding(28.dp)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (seekFlash < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        SideMeters(sideHint)

        if (buffering && error == null && !scrubbing) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(36.dp),
                color = Gold,
                strokeWidth = 2.5.dp
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
                }) { Text("إعادة المحاولة", color = Gold) }
            }
        }

        AnimatedVisibility(
            visible = !inPip && showControls,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180))
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.72f), Color.Transparent)))
                )
                Box(
                    Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.82f))))
                )

                if (locked) {
                    GlassIcon(
                        Icons.Default.LockOpen, "فتح",
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                    ) { locked = false; bumpControls() }
                } else {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        TopBar(
                            title = uri.lastPathSegment ?: "وسائط",
                            speed = speed,
                            audioTracks = audioTracks,
                            textTracks = textTracks,
                            subsOn = subsOn,
                            audioMenu = audioMenu,
                            speedMenu = speedMenu,
                            onAudioMenu = { audioMenu = it },
                            onSpeedMenu = { speedMenu = it },
                            onClose = onExit,
                            onLock = { locked = true; showControls = false },
                            onPip = onPip,
                            onSpeed = { s -> speed = s; controller?.setPlaybackSpeed(s) },
                            onAudio = { selectTrack(controller, it) },
                            onToggleSubs = {
                                val p = controller ?: return@TopBar
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
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        CenterTransport(
                            playing = playing,
                            onBack = { seekBy(-10_000) },
                            onFwd = { seekBy(10_000) },
                            onPlay = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                                bumpControls()
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SeekSection(
                            pos = if (scrubbing) scrubPos else pos,
                            dur = dur,
                            buffered = buffered,
                            onScrub = { f ->
                                if (dur > 0) {
                                    scrubbing = true
                                    scrubPos = (f * dur).toLong()
                                    pos = scrubPos
                                }
                            },
                            onScrubEnd = { f ->
                                if (dur > 0) {
                                    val n = (f * dur).toLong()
                                    controller?.seekTo(n)
                                    pos = n
                                }
                                scrubbing = false
                                bumpControls()
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed class SideHint {
    data class Brightness(val v: Float) : SideHint()
    data class Volume(val v: Float) : SideHint()
    data class Seek(val pos: Long, val dur: Long) : SideHint()
}

@Composable
private fun SideMeters(hint: SideHint?) {
    AnimatedVisibility(
        visible = hint != null,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize()
    ) {
        val h = hint ?: return@AnimatedVisibility
        when (h) {
            is SideHint.Brightness -> Meter(true, h.v, Icons.Default.BrightnessHigh, Alignment.CenterStart)
            is SideHint.Volume -> Meter(false, h.v, Icons.Default.VolumeUp, Alignment.CenterEnd)
            is SideHint.Seek -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE6111111))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(formatPlayerTime(h.pos), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatPlayerTime(h.dur), color = Color.White.copy(0.55f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Meter(left: Boolean, value: Float, icon: androidx.compose.ui.graphics.vector.ImageVector, align: Alignment) {
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        Column(
            Modifier.padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC101010))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.width(5.dp).height(110.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.15f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f))
                        .background(if (left) Gold else Color.White)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("${(value * 100).roundToInt()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    speed: Float,
    audioTracks: List<Pair<Int, String>>,
    textTracks: List<Pair<Int, String>>,
    subsOn: Boolean,
    audioMenu: Boolean,
    speedMenu: Boolean,
    onAudioMenu: (Boolean) -> Unit,
    onSpeedMenu: (Boolean) -> Unit,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onSpeed: (Float) -> Unit,
    onAudio: (Int) -> Unit,
    onToggleSubs: () -> Unit,
    onResize: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIcon(Icons.Default.KeyboardArrowLeft, "رجوع", onClick = onClose)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        GlassIcon(Icons.Default.Lock, "قفل", onClick = onLock)
        Box {
            GlassChip("${if (speed == speed.toInt().toFloat()) speed.toInt() else speed}×") { onSpeedMenu(true) }
            DropdownMenu(speedMenu, { onSpeedMenu(false) }) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    DropdownMenuItem(text = { Text("${s}×") }, onClick = { onSpeed(s); onSpeedMenu(false) })
                }
            }
        }
        if (audioTracks.size > 1) {
            Box {
                GlassIcon(Icons.Default.Audiotrack, "صوت") { onAudioMenu(true) }
                DropdownMenu(audioMenu, { onAudioMenu(false) }) {
                    audioTracks.forEach { (idx, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onAudio(idx); onAudioMenu(false) })
                    }
                }
            }
        }
        if (textTracks.isNotEmpty()) {
            GlassIcon(if (subsOn) Icons.Default.Subtitles else Icons.Default.SubtitlesOff, "ترجمة", onClick = onToggleSubs)
        }
        GlassIcon(Icons.Default.AspectRatio, "مقاس", onClick = onResize)
        GlassIcon(Icons.Default.PictureInPicture, "نافذة", onClick = onPip)
    }
}

@Composable
private fun CenterTransport(
    playing: Boolean,
    onBack: () -> Unit,
    onFwd: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIcon(Icons.Default.Replay10, "10", size = 52.dp, iconSize = 28.dp, onClick = onBack)
        Spacer(Modifier.width(22.dp))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.IconButton(onPlay, modifier = Modifier.fillMaxSize()) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "تشغيل",
                    tint = Color.Black,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.width(22.dp))
        GlassIcon(Icons.Default.Forward10, "10", size = 52.dp, iconSize = 28.dp, onClick = onFwd)
    }
}

@Composable
private fun SeekSection(
    pos: Long,
    dur: Long,
    buffered: Long,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit
) {
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val buf = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        var local by remember { mutableFloatStateOf(frac) }
        var down by remember { mutableStateOf(false) }
        if (!down) local = frac
        val shown = if (down) local else frac
        val thumb = animateFloatAsState(if (down) 1f else 0f, label = "thumb")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(dur) {
                    awaitEachGesture {
                        val d = awaitFirstDown()
                        down = true
                        val f = (d.position.x / size.width).coerceIn(0f, 1f)
                        local = f
                        onScrub(f)
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == d.id } ?: break
                            val nf = (ch.position.x / size.width).coerceIn(0f, 1f)
                            local = nf
                            onScrub(nf)
                            if (!ch.pressed) {
                                onScrubEnd(nf)
                                down = false
                                break
                            }
                        }
                    }
                }
        ) {
            val y = size.height / 2
            val track = 3.dp.toPx()
            val t = 7.dp.toPx() + thumb.value * 4.dp.toPx()
            drawRoundRect(
                Color.White.copy(0.22f),
                Offset(0f, y - track / 2),
                Size(size.width, track),
                CornerRadius(track)
            )
            drawRoundRect(
                Color.White.copy(0.35f),
                Offset(0f, y - track / 2),
                Size(size.width * buf, track),
                CornerRadius(track)
            )
            drawRoundRect(
                Gold,
                Offset(0f, y - track / 2),
                Size(size.width * shown, track),
                CornerRadius(track)
            )
            drawCircle(Gold, t, Offset(size.width * shown, y), style = Fill)
            drawCircle(Color.White, t * 0.35f, Offset(size.width * shown, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlayerTime(pos), color = Color.White.copy(0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(formatPlayerTime(dur), color = Color.White.copy(0.55f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun GlassIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(0.10f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun GlassChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.10f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.TextButton(onClick) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Tracks.named(type: Int): List<Pair<Int, String>> =
    groups.mapIndexedNotNull { index, g ->
        if (g.type != type || g.length == 0) return@mapIndexedNotNull null
        val f = g.getTrackFormat(0)
        index to (f.label ?: f.language ?: "مسار ${index + 1}")
    }

@OptIn(UnstableApi::class)
private fun selectTrack(player: Player?, groupIndex: Int) {
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


