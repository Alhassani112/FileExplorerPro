package com.example.fileexplorerpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fileexplorerpro.usb.UsbViewModel
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.UsbFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbScreen(vm: UsbViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<UsbFile?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    // 🆕 حوار طلب صلاحية USB
    if (s.needsPermission && s.pendingDevice != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissPermissionDialog() },
            icon = { Icon(Icons.Rounded.Usb, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text("صلاحية الوصول إلى USB") },
            text = {
                Text("يحتاج التطبيق صلاحية الوصول إلى جهاز USB هذا لتصفح ملفاته.\n\n" +
                        "اسم الجهاز: ${s.pendingDevice?.usbDevice?.productName ?: "USB"}")
            },
            confirmButton = {
                Button(
                    onClick = { vm.requestPermissionAndConnect(s.pendingDevice!!) }
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text("منح الصلاحية")
                }
            },
            dismissButton = {
                TextButton({ vm.dismissPermissionDialog() }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (s.connected) s.path.lastOrNull()?.name ?: "USB"
                            else "أجهزة USB"
                        )
                        if (s.connected && s.currentDevice != null) {
                            Text(
                                s.currentDevice!!,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (s.connected && s.path.size > 1) {
                        IconButton({ vm.goUp() }) { Icon(Icons.Default.ArrowBack, null) }
                    }
                },
                actions = {
                    IconButton({ vm.refresh() }) { Icon(Icons.Rounded.Refresh, null) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("جارٍ فتح الجهاز...", fontSize = 13.sp)
                    }
                }
                s.error != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline, null,
                            Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(s.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button({ vm.refresh() }) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة المحاولة")
                        }
                    }
                }
                !s.connected -> {
                    if (s.devices.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Rounded.UsbOff, null,
                                Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("لا توجد أجهزة USB", fontSize = 18.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "صل فلاشة عبر OTG واضغط إعادة الفحص",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Button({ vm.refresh() }) {
                                Icon(Icons.Rounded.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("إعادة الفحص")
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Info, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "اضغط على الجهاز لمنح الصلاحية وفتحه",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            items(s.devices) { d ->
                                DeviceCard(d) { vm.connect(d) }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(s.files) { f ->
                            UsbFileRow(
                                f,
                                onClick = { if (f.isDirectory) vm.openDir(f) },
                                onDelete = {
                                    deleteTarget = f
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("حذف \"${deleteTarget?.name}\"؟") },
            confirmButton = {
                TextButton({
                    vm.delete(deleteTarget!!)
                    showDeleteConfirm = false
                    deleteTarget = null
                }, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )) { Text("حذف") }
            },
            dismissButton = {
                TextButton({
                    showDeleteConfirm = false
                    deleteTarget = null
                }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: UsbMassStorageDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Usb, null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(device.usbDevice.productName ?: device.usbDevice.deviceName,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val manufacturer = device.usbDevice.manufacturerName
                if (manufacturer != null) {
                    Text(manufacturer, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronLeft, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsbFileRow(file: UsbFile, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(file.name, maxLines = 1) },
        supportingContent = {
            if (file.isDirectory) Text("مجلد", fontSize = 12.sp)
            else Text(humanSizeUsb(file.length), fontSize = 12.sp)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, null,
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}

private fun humanSizeUsb(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0; if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0; if (m < 1024) return "%.1f MB".format(m)
    return "%.2f GB".format(m / 1024.0)
}
