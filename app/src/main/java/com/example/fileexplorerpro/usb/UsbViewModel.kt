package com.example.fileexplorerpro.usb

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.UsbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsbState(
    val devices: List<UsbMassStorageDevice> = emptyList(),
    val path: List<UsbFile> = emptyList(),
    val files: List<UsbFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val currentDevice: String? = null,
    val needsPermission: Boolean = false,
    val pendingDevice: UsbMassStorageDevice? = null
)

class UsbViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = UsbStorageManager(app)
    private val _s = MutableStateFlow(UsbState())
    val state: StateFlow<UsbState> = _s.asStateFlow()

    fun refresh() {
        _s.value = _s.value.copy(
            devices = manager.listDevices(),
            error = null
        )
    }

    fun connect(device: UsbMassStorageDevice) {
        viewModelScope.launch {
            _s.value = _s.value.copy(loading = true, error = null, needsPermission = false)

            // 1. تحقق من الصلاحية
            if (!manager.hasPermission(device)) {
                _s.value = _s.value.copy(
                    loading = false,
                    needsPermission = true,
                    pendingDevice = device
                )
                return@launch
            }

            // 2. افتح الجهاز
            doOpen(device)
        }
    }

    /** يُستدعى من الواجهة عندما يوافق المستخدم على حوار الصلاحية */
    fun requestPermissionAndConnect(device: UsbMassStorageDevice) {
        viewModelScope.launch {
            _s.value = _s.value.copy(loading = true, error = null)
            val granted = manager.requestPermission(device)
            if (!granted) {
                _s.value = _s.value.copy(
                    loading = false,
                    error = "تم رفض صلاحية USB",
                    needsPermission = false,
                    pendingDevice = null
                )
                return@launch
            }
            doOpen(device)
        }
    }

    fun dismissPermissionDialog() {
        _s.value = _s.value.copy(needsPermission = false, pendingDevice = null)
    }

    private suspend fun doOpen(device: UsbMassStorageDevice) {
        val result = manager.open(device)
        result.fold(
            onSuccess = { root ->
                _s.value = _s.value.copy(
                    connected = true,
                    path = listOf(root),
                    files = manager.listChildren(root),
                    loading = false,
                    error = null,
                    currentDevice = device.usbDevice.productName
                        ?: device.usbDevice.deviceName,
                    needsPermission = false,
                    pendingDevice = null
                )
            },
            onFailure = { e ->
                _s.value = _s.value.copy(
                    loading = false,
                    error = "فشل الاتصال: ${e.message ?: "غير معروف"}",
                    needsPermission = false,
                    pendingDevice = null
                )
            }
        )
    }

    fun openDir(dir: UsbFile) {
        viewModelScope.launch {
            val children = withContext(Dispatchers.IO) { manager.listChildren(dir) }
            _s.value = _s.value.copy(files = children, path = _s.value.path + dir)
        }
    }

    fun goUp(): Boolean {
        val p = _s.value.path
        if (p.size <= 1) return false
        val parent = p[p.size - 2]
        viewModelScope.launch {
            val children = withContext(Dispatchers.IO) { manager.listChildren(parent) }
            _s.value = _s.value.copy(path = p.dropLast(1), files = children)
        }
        return true
    }

    fun delete(f: UsbFile) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { f.delete() }
                _s.value.path.lastOrNull()?.let { cur ->
                    val children = withContext(Dispatchers.IO) { manager.listChildren(cur) }
                    _s.value = _s.value.copy(files = children)
                }
            } catch (e: Exception) {
                _s.value = _s.value.copy(error = "فشل الحذف: ${e.message}")
            }
        }
    }

    fun disconnect() {
        _s.value = UsbState(devices = _s.value.devices)
    }
}
