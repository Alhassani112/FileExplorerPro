package com.example.fileexplorerpro.usb
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.UsbFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
data class UsbState(val devices: List<UsbMassStorageDevice> = emptyList(),
    val path: List<UsbFile> = emptyList(), val files: List<UsbFile> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val connected: Boolean = false,
    val currentDevice: String? = null)
class UsbViewModel(app: Application) : AndroidViewModel(app) {
    private val _s = MutableStateFlow(UsbState())
    val state: StateFlow<UsbState> = _s.asStateFlow()
    private var root: UsbFile? = null
    fun refresh() { _s.value = _s.value.copy(devices =
        runCatching { UsbMassStorageDevice.getMassStorageDevices(getApplication()).toList() }
            .getOrDefault(emptyList())) }
    fun connect(d: UsbMassStorageDevice) { viewModelScope.launch {
        _s.value = _s.value.copy(loading = true, error = null)
        try { d.init()
            root = d.partitions.firstOrNull()?.rootDirectory
            val list = root?.listFiles()?.toList() ?: emptyList()
            _s.value = _s.value.copy(connected = true, path = listOfNotNull(root),
                files = list, loading = false, currentDevice = d.usbDevice.deviceName)
        } catch (e: Exception) {
            _s.value = _s.value.copy(error = "\u062e\u0637\u0623: ${e.message}", loading = false) } } }
    fun openDir(d: UsbFile) { _s.value = _s.value.copy(
        files = d.listFiles().toList(), path = _s.value.path + d) }
    fun goUp(): Boolean { val p = _s.value.path
        if (p.size <= 1) return false
        val par = p[p.size-2]
        _s.value = _s.value.copy(path = p.dropLast(1), files = par.listFiles().toList())
        return true }
    fun delete(f: UsbFile) { viewModelScope.launch {
        try { f.delete()
            _s.value.path.lastOrNull()?.let { _s.value = _s.value.copy(files = it.listFiles().toList()) }
        } catch (e: Exception) {
            _s.value = _s.value.copy(error = "\u0641\u0634\u0644 \u0627\u0644\u062d\u0630\u0641: ${e.message}") } } }
    fun disconnect() { root = null; _s.value = UsbState(devices = _s.value.devices) }
}
