package com.example.fileexplorerpro.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.fileexplorerpro.R
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var hint: LinearLayout
    private lateinit var hintText: TextView
    private lateinit var errorView: TextView
    private lateinit var titleView: TextView
    private lateinit var speedBtn: TextView
    private val resume by lazy { PlayerResumeStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var mediaUri: Uri? = null
    private var pendingResume = 0L
    private var speed = 1f
    private var resize = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var brightness = 0.5f
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        hint = findViewById(R.id.gesture_hint)
        hintText = findViewById(R.id.gesture_hint_text)
        errorView = findViewById(R.id.player_error)
        brightness = currentBrightness()

        mediaUri = intent?.data
        if (mediaUri == null) {
            finish()
            return
        }
        bindChrome()
        startPlayback(mediaUri!!)
        attachGestures()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let {
            mediaUri = it
            startPlayback(it)
        }
    }

    private fun bindChrome() {
        playerView.post {
            titleView = playerView.findViewById(R.id.player_title) ?: return@post
            speedBtn = playerView.findViewById(R.id.btn_speed) ?: return@post
            titleView.text = mediaUri?.lastPathSegment ?: getString(R.string.app_name)
            playerView.findViewById<View>(R.id.btn_close)?.setOnClickListener { finish() }
            playerView.findViewById<View>(R.id.btn_pip)?.setOnClickListener { enterPip() }
            playerView.findViewById<View>(R.id.btn_resize)?.setOnClickListener { cycleResize() }
            speedBtn.setOnClickListener { cycleSpeed() }
        }
    }

    private fun startPlayback(uri: Uri) {
        errorView.visibility = View.GONE
        player?.release()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player = exo
        playerView.player = exo
        playerView.resizeMode = resize
        pendingResume = resume.get(uri)
        exo.setMediaItem(SidecarSubtitles.mediaItem(uri))
        exo.prepare()
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && pendingResume > 5_000L) {
                    val d = exo.duration
                    if (d <= 0 || pendingResume < d - 8_000L) exo.seekTo(pendingResume)
                    pendingResume = 0L
                }
                if (state == Player.STATE_ENDED) {
                    mediaUri?.let { resume.save(it, 0L, exo.duration) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorView.visibility = View.VISIBLE
                errorView.text = error.localizedMessage ?: "خطأ في التشغيل"
            }
        })
        playerView.post {
            if (::titleView.isInitialized) {
                titleView.text = uri.lastPathSegment ?: ""
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        var scrollMode = 0
        val detector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scrollMode = 0
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                playerView.performClick()
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return false
                val w = playerView.width.toFloat().coerceAtLeast(1f)
                when {
                    e.x < w / 3f -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                    e.x > w * 2f / 3f -> {
                        val d = p.duration
                        p.seekTo(if (d > 0) (p.currentPosition + 10_000).coerceAtMost(d) else p.currentPosition + 10_000)
                    }
                    else -> if (p.isPlaying) p.pause() else p.play()
                }
                flash(if (e.x < w / 2f) "−10 ث" else "+10 ث")
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val start = e1 ?: return false
                val w = playerView.width.toFloat().coerceAtLeast(1f)
                val h = playerView.height.toFloat().coerceAtLeast(1f)
                if (scrollMode == 0) {
                    scrollMode = if (abs(distanceX) > abs(distanceY)) 3
                    else if (start.x < w * 0.45f) 1 else 2
                }
                val p = player
                when (scrollMode) {
                    1 -> {
                        brightness = (brightness + distanceY / h).coerceIn(0.05f, 1f)
                        setWindowBrightness(brightness)
                        flash("السطوع ${(brightness * 100).toInt()}%")
                    }
                    2 -> {
                        val vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()
                        val next = (vol + distanceY / h).coerceIn(0f, 1f)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (next * maxVol).toInt(), 0)
                        flash("الصوت ${(next * 100).toInt()}%")
                    }
                    3 -> if (p != null && p.duration > 0) {
                        val skip = (-distanceX / w * p.duration * 0.35f).toLong()
                        p.seekTo((p.currentPosition + skip).coerceIn(0, p.duration))
                        flash(formatTime(p.currentPosition))
                    }
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun cycleSpeed() {
        val i = (speeds.indexOfFirst { it == speed } + 1) % speeds.size
        speed = speeds[i]
        player?.setPlaybackSpeed(speed)
        if (::speedBtn.isInitialized) {
            speedBtn.text = if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"
        }
        flash("${speedBtn.text}")
    }

    private fun cycleResize() {
        resize = when (resize) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = resize
        flash(
            when (resize) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ملء مع قص"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "تمديد"
                else -> "ملاءمة"
            }
        )
    }

    private fun flash(text: String) {
        hintText.text = text
        hint.visibility = View.VISIBLE
        handler.removeCallbacks(hideHint)
        handler.postDelayed(hideHint, 700)
    }

    private val hideHint = Runnable { hint.visibility = View.GONE }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) enterPip()
    }

    override fun onStop() {
        mediaUri?.let { uri ->
            player?.let { resume.save(uri, it.currentPosition, it.duration) }
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        playerView.useController = !isInPictureInPictureMode
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        return if (b in 0f..1f) b else 0.5f
    }

    private fun setWindowBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
