package com.example.fileexplorerpro.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class StorageVolume(
    val name: String,
    val path: String,
    val uri: Uri,
    val type: VolumeType
)

enum class VolumeType { INTERNAL, SDCARD, USB, DOWNLOADS, PICTURES, MUSIC, MOVIES, DOCUMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenVolume: (StorageVolume) -> Unit,
    onPickFolder: () -> Unit,
    onOpenUsb: () -> Unit
) {
    val volumes = remember { detectVolumes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مستكشف الملفات", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onPickFolder) {
                        Icon(Icons.Default.CreateNewFolder, null)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("وحدات التخزين",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
            items(volumes) { vol ->
                VolumeCard(vol, onClick = { onOpenVolume(vol) })
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("أدوات",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenUsb),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Usb, null, Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("أجهزة USB", fontWeight = FontWeight.Bold)
                            Text("تصفح الفلاشات المتصلة", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(vol: StorageVolume, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (vol.type) {
                    VolumeType.INTERNAL -> Icons.Default.Storage
                    VolumeType.SDCARD -> Icons.Default.SdCard
                    VolumeType.USB -> Icons.Default.Usb
                    VolumeType.DOWNLOADS -> Icons.Default.Download
                    VolumeType.PICTURES -> Icons.Default.Image
                    VolumeType.MUSIC -> Icons.Default.MusicNote
                    VolumeType.MOVIES -> Icons.Default.Movie
                    VolumeType.DOCUMENTS -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(vol.name, fontWeight = FontWeight.Bold)
                val free = remember(vol.path) { try { File(vol.path).freeSpace } catch (_: Exception) { 0L } }
                val total = remember(vol.path) { try { File(vol.path).totalSpace } catch (_: Exception) { 0L } }
                if (total > 0) {
                    val usedPct = ((total - free).toFloat() / total).coerceIn(0f, 1f)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usedPct },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = when {
                            usedPct > 0.9f -> MaterialTheme.colorScheme.error
                            usedPct > 0.7f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("متاح: ${humanSizeHome(free)} من ${humanSizeHome(total)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(vol.path, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
    }
}

private fun detectVolumes(): List<StorageVolume> {
    val list = mutableListOf<StorageVolume>()
    val ext = Environment.getExternalStorageDirectory()
    // التخزين الداخلي
    if (ext != null && ext.exists()) {
        list += StorageVolume(
            name = "التخزين الداخلي",
            path = ext.absolutePath,
            uri = Uri.fromFile(ext),
            type = VolumeType.INTERNAL
        )
        // المجلدات الشائعة
        addIfExists(list, File(ext, "Download"), "التنزيلات", VolumeType.DOWNLOADS)
        addIfExists(list, File(ext, "DCIM"), "الصور والكاميرا", VolumeType.PICTURES)
        addIfExists(list, File(ext, "Pictures"), "الصور", VolumeType.PICTURES)
        addIfExists(list, File(ext, "Music"), "الموسيقى", VolumeType.MUSIC)
        addIfExists(list, File(ext, "Movies"), "الأفلام", VolumeType.MOVIES)
        addIfExists(list, File(ext, "Documents"), "المستندات", VolumeType.DOCUMENTS)
    }
    // بطاقة SD
    try {
        val sdcard = File("/storage")
        sdcard.listFiles()?.forEach { f ->
            val n = f.name
            if (f.isDirectory && n != "emulated" && n != "self" && !n.startsWith(".")) {
                if (f.canRead()) {
                    list += StorageVolume(
                        name = "بطاقة SD",
                        path = f.absolutePath,
                        uri = Uri.fromFile(f),
                        type = VolumeType.SDCARD
                    )
                }
            }
        }
    } catch (_: Exception) {}
    return list
}

private fun addIfExists(list: MutableList<StorageVolume>, f: File, name: String, type: VolumeType) {
    if (f.exists() && f.isDirectory && f.canRead()) {
        list += StorageVolume(name, f.absolutePath, Uri.fromFile(f), type)
    }
}

private fun humanSizeHome(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0; if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0; if (m < 1024) return "%.1f MB".format(m)
    val g = m / 1024.0; if (g < 1024) return "%.2f GB".format(g)
    return "%.2f TB".format(g / 1024.0)
}
