package com.example.fileexplorerpro.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class VolumeType { INTERNAL, SDCARD, USB, DOWNLOADS, PICTURES, MUSIC, MOVIES, DOCUMENTS, DCIM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenVolume: (StorageVolume) -> Unit,
    onPickFolder: () -> Unit,
    onOpenUsb: () -> Unit
) {
    val volumes = remember { detectVolumes() }
    val internal = volumes.firstOrNull { it.type == VolumeType.INTERNAL }
    val quickAccess = volumes.filter {
        it.type in listOf(
            VolumeType.DOWNLOADS, VolumeType.DCIM,
            VolumeType.PICTURES, VolumeType.MUSIC,
            VolumeType.MOVIES, VolumeType.DOCUMENTS
        )
    }
    val otherVolumes = volumes.filter {
        it.type == VolumeType.SDCARD || it.type == VolumeType.USB
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مستكشف الملفات",
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("تصفح ملفاتك بسهولة",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onPickFolder) {
                        Icon(Icons.Rounded.FolderOpen, null)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ═══ بطاقة التخزين الرئيسية ═══
            if (internal != null) {
                item { MainStorageCard(internal) { onOpenVolume(internal) } }
            }

            // ═══ الوصول السريع ═══
            if (quickAccess.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "الوصول السريع",
                        subtitle = "${quickAccess.size} فئات"
                    )
                }
                item {
                    QuickAccessGrid(quickAccess, onOpenVolume)
                }
            }

            // ═══ وحدات أخرى ═══
            if (otherVolumes.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "وحدات أخرى",
                        subtitle = "${otherVolumes.size} وحدة"
                    )
                }
                items(otherVolumes.size) { idx ->
                    val vol = otherVolumes[idx]
                    OtherVolumeRow(vol) { onOpenVolume(vol) }
                }
            }

            // ═══ أدوات ═══
            item {
                SectionHeader(title = "أدوات", subtitle = null)
            }
            item {
                ToolsCard(onOpenUsb = onOpenUsb, onPickFolder = onPickFolder)
            }
        }
    }
}

/* ═══════════ بطاقة التخزين الرئيسية ═══════════ */
@Composable
private fun MainStorageCard(vol: StorageVolume, onClick: () -> Unit) {
    val free = remember(vol.path) {
        try { File(vol.path).freeSpace } catch (_: Exception) { 0L }
    }
    val total = remember(vol.path) {
        try { File(vol.path).totalSpace } catch (_: Exception) { 0L }
    }
    val used = (total - free).coerceAtLeast(0)
    val usedPct = if (total > 0) used.toFloat() / total else 0f
    val primary = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // دائرة التقدم
            Box(
                Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val diam = size.minDimension - stroke
                    drawArc(
                        color = Color.White.copy(alpha = 0.25f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(diam, diam),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * usedPct,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(diam, diam),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(usedPct * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text("مستخدم", color = Color.White.copy(0.9f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("التخزين الداخلي",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null,
                        tint = Color.White.copy(0.9f),
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("متاح ${humanSizeHome(free)}",
                        color = Color.White.copy(0.95f),
                        fontSize = 13.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, null,
                        tint = Color.White.copy(0.8f),
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("الإجمالي ${humanSizeHome(total)}",
                        color = Color.White.copy(0.85f),
                        fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فتح", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ArrowForward, null,
                        tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/* ═══════════ شبكة الوصول السريع ═══════════ */
@Composable
private fun QuickAccessGrid(items: List<StorageVolume>, onClick: (StorageVolume) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(items) { vol ->
            QuickCard(vol) { onClick(vol) }
        }
    }
}

@Composable
private fun QuickCard(vol: StorageVolume, onClick: () -> Unit) {
    val (icon, color) = iconAndColorFor(vol.type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                vol.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
    }
}

/* ═══════════ صف الوحدات الأخرى ═══════════ */
@Composable
private fun OtherVolumeRow(vol: StorageVolume, onClick: () -> Unit) {
    val (icon, color) = iconAndColorFor(vol.type)
    val free = remember(vol.path) {
        try { File(vol.path).freeSpace } catch (_: Exception) { 0L }
    }
    val total = remember(vol.path) {
        try { File(vol.path).totalSpace } catch (_: Exception) { 0L }
    }
    val usedPct = if (total > 0) ((total - free).toFloat() / total) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(vol.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (total > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usedPct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("متاح ${humanSizeHome(free)} من ${humanSizeHome(total)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(vol.path, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
            Icon(Icons.Rounded.ChevronLeft, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ═══════════ بطاقة الأدوات ═══════════ */
@Composable
private fun ToolsCard(onOpenUsb: () -> Unit, onPickFolder: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolButton(
            icon = Icons.Rounded.Usb,
            label = "أجهزة USB",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onOpenUsb
        )
        ToolButton(
            icon = Icons.Rounded.CreateNewFolder,
            label = "اختيار مجلد",
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = onPickFolder
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/* ═══════════ عنوان قسم ═══════════ */
@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(4.dp).height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ═══════════ أدوات مساعدة ═══════════ */
private fun iconAndColorFor(type: VolumeType): Pair<ImageVector, Color> = when (type) {
    VolumeType.INTERNAL -> Icons.Rounded.Storage to Color(0xFF2196F3)
    VolumeType.SDCARD -> Icons.Rounded.SdCard to Color(0xFF9C27B0)
    VolumeType.USB -> Icons.Rounded.Usb to Color(0xFF00BCD4)
    VolumeType.DOWNLOADS -> Icons.Rounded.Download to Color(0xFF4CAF50)
    VolumeType.DCIM -> Icons.Rounded.PhotoCamera to Color(0xFFE91E63)
    VolumeType.PICTURES -> Icons.Rounded.Image to Color(0xFFFF9800)
    VolumeType.MUSIC -> Icons.Rounded.MusicNote to Color(0xFF8BC34A)
    VolumeType.MOVIES -> Icons.Rounded.Movie to Color(0xFFF44336)
    VolumeType.DOCUMENTS -> Icons.Rounded.Description to Color(0xFF607D8B)
}

private fun detectVolumes(): List<StorageVolume> {
    val list = mutableListOf<StorageVolume>()
    val ext = Environment.getExternalStorageDirectory()
    if (ext != null && ext.exists()) {
        list += StorageVolume(
            name = "التخزين الداخلي",
            path = ext.absolutePath,
            uri = Uri.fromFile(ext),
            type = VolumeType.INTERNAL
        )
        addIfExists(list, File(ext, "Download"), "التنزيلات", VolumeType.DOWNLOADS)
        addIfExists(list, File(ext, "DCIM"), "الصور والكاميرا", VolumeType.DCIM)
        addIfExists(list, File(ext, "Pictures"), "الصور", VolumeType.PICTURES)
        addIfExists(list, File(ext, "Music"), "الموسيقى", VolumeType.MUSIC)
        addIfExists(list, File(ext, "Movies"), "الأفلام", VolumeType.MOVIES)
        addIfExists(list, File(ext, "Documents"), "المستندات", VolumeType.DOCUMENTS)
    }
    try {
        val storage = File("/storage")
        storage.listFiles()?.forEach { f ->
            val n = f.name
            if (f.isDirectory && n != "emulated" && n != "self" && !n.startsWith(".")) {
                if (f.canRead() && f.listFiles() != null) {
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

private fun addIfExists(
    list: MutableList<StorageVolume>,
    f: File,
    name: String,
    type: VolumeType
) {
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
