#!/bin/bash
D="app/src/main/java/com/example/fileexplorerpro"

# ============ 1. FileBrowserScreen.kt - إضافة import الناقص ============
# نستخدم sed لإضافة السطر بعد LazyColumn import
sed -i 's|import androidx.compose.foundation.lazy.LazyColumn|import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items|' \
    $D/ui/FileBrowserScreen.kt

# ============ 2. UsbViewModel.kt - استخدام FileSystem بدل rootDirectory ============
cat > $D/usb/UsbViewModel.kt <<'EOF'
package com.example.fileexplorerpro.usb

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.UsbFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsbState(
    val devices: List<UsbMassStorageDevice> = emptyList(),
    val path: List<UsbFile> = emptyList(),
    val files: List<UsbFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val currentDevice: String? = null
)

class UsbViewModel(app: Application) : AndroidViewModel(app) {

    private val _s = MutableStateFlow(UsbState())
    val state: StateFlow<UsbState> = _s.asStateFlow()
    private var root: UsbFile? = null

    fun refresh() {
        _s.value = _s.value.copy(
            devices = try {
                UsbMassStorageDevice.getMassStorageDevices(getApplication()).toList()
            } catch (_: Exception) { emptyList() }
        )
    }

    fun connect(d: UsbMassStorageDevice) {
        viewModelScope.launch {
            _s.value = _s.value.copy(loading = true, error = null)
            try {
                d.init()
                val partitions = d.partitions
                if (partitions.isEmpty()) {
                    _s.value = _s.value.copy(
                        loading = false,
                        error = "لا توجد أقسام على الجهاز"
                    )
                    return@launch
                }
                val partition = partitions[0]
                // محاولة متعددة للحصول على المجلد الجذر
                val fs = try { partition.fileSystem } catch (_: Exception) { null }
                root = fs?.rootDirectory
                val list = try { root?.listFiles()?.toList() ?: emptyList() }
                           catch (_: Exception) { emptyList() }
                _s.value = _s.value.copy(
                    connected = true,
                    path = listOfNotNull(root),
                    files = list,
                    loading = false,
                    currentDevice = d.usbDevice.deviceName
                )
            } catch (e: Exception) {
                _s.value = _s.value.copy(
                    loading = false,
                    error = "خطأ: ${e.message ?: "غير معروف"}"
                )
            }
        }
    }

    fun openDir(d: UsbFile) {
        _s.value = _s.value.copy(
            files = try { d.listFiles().toList() } catch (_: Exception) { emptyList() },
            path = _s.value.path + d
        )
    }

    fun goUp(): Boolean {
        val p = _s.value.path
        if (p.size <= 1) return false
        val parent = p[p.size - 2]
        _s.value = _s.value.copy(
            path = p.dropLast(1),
            files = try { parent.listFiles().toList() } catch (_: Exception) { emptyList() }
        )
        return true
    }

    fun delete(f: UsbFile) {
        viewModelScope.launch {
            try {
                f.delete()
                _s.value.path.lastOrNull()?.let { cur ->
                    _s.value = _s.value.copy(
                        files = try { cur.listFiles().toList() } catch (_: Exception) { emptyList() }
                    )
                }
            } catch (e: Exception) {
                _s.value = _s.value.copy(error = "فشل الحذف: ${e.message}")
            }
        }
    }

    fun disconnect() {
        root = null
        _s.value = UsbState(devices = _s.value.devices)
    }
}
EOF

# ============ 3. UsbScreen.kt - إصلاح بنية when المكسورة ============
cat > $D/ui/UsbScreen.kt <<'EOF'
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
import com.github.mjdev.libaums.fs.UsbFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbScreen(vm: UsbViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<UsbFile?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

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
                    IconButton({ vm.refresh() }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                s.error != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline, null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(s.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button({ vm.refresh() }) { Text("إعادة المحاولة") }
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
                                Icons.Default.Usb, null,
                                Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("لا توجد أجهزة USB", fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "صل جهاز USB OTG",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button({ vm.refresh() }) { Text("إعادة الفحص") }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(8.dp)) {
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
        modifier = Modifier.fillMaxWidth().padding(4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Usb, null,
                Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(device.usbDevice.deviceName, fontWeight = FontWeight.Bold)
                val manufacturer = device.usbDevice.manufacturerName ?: "USB Device"
                Text(
                    manufacturer,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsbFileRow(file: UsbFile, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
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
                Icon(
                    Icons.Default.Delete, null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

private fun humanSizeUsb(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0
    if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0
    if (m < 1024) return "%.1f MB".format(m)
    return "%.2f GB".format(m / 1024.0)
}
EOF

# ============ 4. PlayerActivity.kt - استبدال setOverride بـ addOverride ============
sed -i 's|\.setOverride(override)|.addOverride(override)|g' $D/player/PlayerActivity.kt

echo "✅ تم تطبيق جميع الإصلاحات الأربعة"
