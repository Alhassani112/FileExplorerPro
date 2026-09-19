package com.example.fileexplorerpro.ui
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fileexplorerpro.usb.UsbViewModel
import com.github.mjdev.libaums.UsbMassStorageDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UsbScreen(vm: UsbViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<com.github.mjdev.libaums.fs.UsbFile?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }
    Scaffold(topBar = { TopAppBar(
        title = {
            Column {
                Text(if (s.connected) s.path.lastOrNull()?.name ?: "USB" else "\u0623\u062c\u0647\u0632\u0629 USB")
                if (s.connected && s.currentDevice != null) {
                    Text(s.currentDevice!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        navigationIcon = { if (s.connected && s.path.size > 1)
            IconButton({ vm.goUp() }) { Icon(Icons.Default.ArrowBack, null) } },
        actions = { IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, null) } }) }
    ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) {
        when {
            s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            s.error != null -> Column(Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(s.error!!, color = MaterialTheme.colorScheme.error) }
            !s.connected -> if (s.devices.isEmpty()) Column(Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Usb, null, Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("\u0644\u0627 \u062a\u0648\u062c\u062f \u0623\u062c\u0647\u0632\u0629 USB", fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("\u062a\u0635\u0644 \u062c\u0647\u0627\u0632 USB OTG", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(s.devices) { d ->
                    DeviceCard(d, { vm.connect(d) }) }
            } else LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(s.files) { f ->
                    UsbFileRow(f, { if (f.isDirectory) vm.openDir(f) },
                        { deleteTarget = f; showDeleteConfirm = true }) }
            }
        }
    } }
    if (showDeleteConfirm && deleteTarget != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false },
            title = { Text("\u062a\u0623\u0643\u064a\u062f \u0627\u0644\u062d\u0630\u0641") },
            text = { Text("\u062d\u0630\u0641 \"${deleteTarget?.name}\"?") },
            confirmButton = { TextButton({ vm.delete(deleteTarget!!); showDeleteConfirm = false; deleteTarget = null },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("\u062d\u0630\u0641") } },
            dismissButton = { TextButton({ showDeleteConfirm = false; deleteTarget = null }) { Text("\u0625\u0644\u063a\u0627\u0621") } })
    }
}

@Composable
private fun DeviceCard(device: UsbMassStorageDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Usb, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(device.usbDevice.deviceName, fontWeight = FontWeight.Bold)
                val manufacturer = device.usbDevice.manufacturerName ?: "USB Device"
                Text(manufacturer, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UsbFileRow(file: com.github.mjdev.libaums.fs.UsbFile, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(file.name, maxLines = 1) },
        supportingContent = {
            if (file.isDirectory) Text("\u0645\u062c\u0644\u062f", fontSize = 12.sp)
            else Text(humanSizeUsb(file.length), fontSize = 12.sp) },
        trailingContent = { IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) } })
}

private fun humanSizeUsb(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0; if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0; if (m < 1024) return "%.1f MB".format(m)
    return "%.2f GB".format(m / 1024.0)
}
