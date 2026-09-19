package com.example.fileexplorerpro.viewer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val uri = intent.data
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Viewer(uri) { finish() } } }
    }
}
@Composable fun Viewer(uri: Uri?, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(uri, null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTransformGestures { _, p, z, _ ->
                    scale = (scale * z).coerceIn(0.5f, 8f)
                    if (scale > 1f) { ox += p.x; oy += p.y }
                    else { ox = 0f; oy = 0f } } }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy))
        if (showControls) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton({ scale = (scale * 1.2f).coerceAtMost(8f) }) {
                    Icon(Icons.Default.ZoomIn, null, tint = Color.White) }
                IconButton({ scale = (scale / 1.2f).coerceAtLeast(0.5f) }) {
                    Icon(Icons.Default.ZoomOut, null, tint = Color.White) }
            }
            Text("${((scale - 1) * 100 + 100).toInt()}%", color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}
