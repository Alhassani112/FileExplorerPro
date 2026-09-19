package com.example.fileexplorerpro.ui
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fileexplorerpro.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
enum class SortMode { NAME, DATE, SIZE, TYPE }
enum class ViewMode { LIST, GRID }
data class BrowserState(
    val currentUri: Uri? = null, val items: List<FileItem> = emptyList(),
    val loading: Boolean = false, val showHidden: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID, val sortMode: SortMode = SortMode.NAME,
    val searchQuery: String = "", val backStack: List<Uri> = emptyList(),
    val clipboardCount: Int = 0, val isCutClipboard: Boolean = false,
    val selectedItems: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val storageInfo: FileRepository.StorageInfo? = null)
class FileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileRepository(app)
    private val _s = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _s.asStateFlow()
    fun openDirectory(uri: Uri, push: Boolean = true) { viewModelScope.launch {
        val prev = _s.value.currentUri
        _s.value = _s.value.copy(loading = true, currentUri = uri, selectedItems = emptySet(), isSelectionMode = false)
        val list = repo.listDirectory(uri, _s.value.showHidden)
        _s.value = _s.value.copy(items = sort(list, _s.value.sortMode), loading = false,
            backStack = if (push && prev != null && prev != uri) _s.value.backStack + prev else _s.value.backStack)
        if (push && _s.value.backStack.isEmpty()) {
            try { _s.value = _s.value.copy(storageInfo = repo.getStorageInfo()) } catch (_: Exception) {} }
    } }
    fun goBack(): Boolean {
        if (_s.value.isSelectionMode) { _s.value = _s.value.copy(selectedItems = emptySet(), isSelectionMode = false); return true }
        val bs = _s.value.backStack; if (bs.isEmpty()) return false
        val p = bs.last()
        _s.value = _s.value.copy(backStack = bs.dropLast(1))
        openDirectory(p, false); return true }
    fun toggleHidden() { _s.value = _s.value.copy(showHidden = !_s.value.showHidden)
        _s.value.currentUri?.let { openDirectory(it, false) } }
    fun toggleViewMode() { _s.value = _s.value.copy(
        viewMode = if (_s.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }
    fun setSort(m: SortMode) { _s.value = _s.value.copy(sortMode = m, items = sort(_s.value.items, m)) }
    fun setSearch(q: String) { _s.value = _s.value.copy(searchQuery = q) }
    fun delete(item: FileItem) { viewModelScope.launch {
        if (repo.delete(item.uri)) _s.value = _s.value.copy(items = _s.value.items - item) } }
    fun deleteSelected() { viewModelScope.launch {
        val selected = _s.value.items.filter { it.uri.toString() in _s.value.selectedItems }
        selected.forEach { repo.delete(it.uri) }
        _s.value = _s.value.copy(items = _s.value.items - selected.toSet(), selectedItems = emptySet(), isSelectionMode = false)
        _s.value.currentUri?.let { openDirectory(it, false) }
    } }
    fun rename(item: FileItem, n: String) { viewModelScope.launch {
        if (repo.rename(item.uri, n)) _s.value.currentUri?.let { openDirectory(it, false) } } }
    fun createFolder(n: String) { viewModelScope.launch {
        _s.value.currentUri?.let { repo.createFolder(it, n); openDirectory(it, false) } } }
    fun copySelected(items: List<FileItem>) {
        val toCopy = if (items.isEmpty()) _s.value.items.filter { it.uri.toString() in _s.value.selectedItems } else items
        ClipboardManager.set(toCopy, ClipOperation.COPY, _s.value.currentUri ?: return)
        _s.value = _s.value.copy(clipboardCount = toCopy.size, isCutClipboard = false,
            selectedItems = emptySet(), isSelectionMode = false) }
    fun cutSelected(items: List<FileItem>) {
        val toCut = if (items.isEmpty()) _s.value.items.filter { it.uri.toString() in _s.value.selectedItems } else items
        ClipboardManager.set(toCut, ClipOperation.CUT, _s.value.currentUri ?: return)
        _s.value = _s.value.copy(clipboardCount = toCut.size, isCutClipboard = true,
            selectedItems = emptySet(), isSelectionMode = false) }
    fun pasteHere() {
        val e = ClipboardManager.get() ?: return
        val dest = _s.value.currentUri ?: return
        FileOperationWorker.enqueue(getApplication(),
            if (e.operation == ClipOperation.COPY) "copy" else "move",
            e.items.map { it.uri }, dest)
        ClipboardManager.clear()
        _s.value = _s.value.copy(clipboardCount = 0, isCutClipboard = false) }
    fun cancelClipboard() { ClipboardManager.clear()
        _s.value = _s.value.copy(clipboardCount = 0, isCutClipboard = false) }
    fun toggleSelection(uri: String) {
        val current = _s.value.selectedItems.toMutableSet()
        if (uri in current) current.remove(uri) else current.add(uri)
        _s.value = _s.value.copy(selectedItems = current, isSelectionMode = current.isNotEmpty()) }
    fun clearSelection() { _s.value = _s.value.copy(selectedItems = emptySet(), isSelectionMode = false) }
    fun selectAll() { _s.value = _s.value.copy(selectedItems = _s.value.items.map { it.uri.toString() }.toSet(), isSelectionMode = true) }
    private fun sort(l: List<FileItem>, m: SortMode): List<FileItem> {
        val (d, f) = l.partition { it.isDirectory }
        val c = when (m) {
            SortMode.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.DATE -> compareByDescending { it.lastModified }
            SortMode.SIZE -> compareByDescending { it.size }
            SortMode.TYPE -> compareBy { it.extension } }
        return d.sortedWith(c) + f.sortedWith(c) }
}
