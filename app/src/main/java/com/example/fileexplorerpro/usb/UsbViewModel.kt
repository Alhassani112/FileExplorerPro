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
