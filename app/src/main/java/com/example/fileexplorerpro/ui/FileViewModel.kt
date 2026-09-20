package com.example.fileexplorerpro.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fileexplorerpro.data.ClipOperation
import com.example.fileexplorerpro.data.ClipboardManager
import com.example.fileexplorerpro.data.FileItem
import com.example.fileexplorerpro.data.FileNames
import com.example.fileexplorerpro.data.FileOperationWorker
import com.example.fileexplorerpro.data.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortMode { NAME, DATE, SIZE, TYPE }
enum class ViewMode { LIST, GRID }

data class BrowserState(
    val currentUri: Uri? = null,
    val currentPath: String? = null,
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val showHidden: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortMode: SortMode = SortMode.NAME,
    val searchQuery: String = "",
    val backStack: List<Pair<Uri?, String?>> = emptyList(),
    val clipboardCount: Int = 0,
    val message: String? = null
)

class FileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileRepository(app)
    private val _s = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _s.asStateFlow()
    private var listJob: Job? = null

    fun openItem(item: FileItem) {
        if (!item.isDirectory) return
        if (item.uri.scheme == "file") {
            val path = item.uri.path ?: return
            openPath(path)
        } else {
            openUri(item.uri)
        }
    }

    fun openPath(path: String) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val prevUri = _s.value.currentUri
            val prevPath = _s.value.currentPath
            _s.update { it.copy(loading = true, currentPath = path) }
            val list = withContext(Dispatchers.IO) {
                repo.listByPath(path, _s.value.showHidden)
            }
            _s.update { st ->
                st.copy(
                    items = sort(list, st.sortMode),
                    loading = false,
                    currentUri = null,
                    currentPath = path,
                    backStack = if (prevPath != null && prevPath != path)
                        st.backStack + (prevUri to prevPath) else st.backStack
                )
            }
        }
    }

    fun openUri(uri: Uri) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val prevUri = _s.value.currentUri
            val prevPath = _s.value.currentPath
            _s.update { it.copy(loading = true, currentUri = uri, currentPath = null) }
            val list = repo.listDirectory(uri, _s.value.showHidden)
            _s.update { st ->
                st.copy(
                    items = sort(list, st.sortMode),
                    loading = false,
                    backStack = if (prevUri != null && prevUri != uri)
                        st.backStack + (prevUri to prevPath) else st.backStack
                )
            }
        }
    }

    @Deprecated("استخدم openUri")
    fun openDirectory(uri: Uri, push: Boolean = true) = openUri(uri)

    fun goBack(): Boolean {
        val bs = _s.value.backStack
        if (bs.isEmpty()) return false
        val (prevUri, prevPath) = bs.last()
        _s.update { it.copy(backStack = bs.dropLast(1)) }
        if (prevPath != null) openPath(prevPath) else prevUri?.let { openUri(it) }
        return true
    }

    fun toggleHidden() {
        _s.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun toggleViewMode() {
        _s.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun setSort(m: SortMode) {
        _s.update { it.copy(sortMode = m, items = sort(it.items, m)) }
    }

    fun setSearch(q: String) {
        _s.update { it.copy(searchQuery = q) }
    }

    fun consumeMessage() {
        _s.update { it.copy(message = null) }
    }

    fun refresh() {
        _s.value.currentPath?.let { openPath(it) }
            ?: _s.value.currentUri?.let { openUri(it) }
    }

    fun delete(item: FileItem) {
        viewModelScope.launch {
            val ok = repo.delete(item.uri)
            if (ok) _s.update { it.copy(items = it.items - item) }
            else _s.update { it.copy(message = "تعذر حذف العنصر") }
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val safe = FileNames.sanitize(newName)
            if (safe == null) {
                _s.update { it.copy(message = "اسم غير صالح") }
                return@launch
            }
            val ok = repo.rename(item.uri, safe)
            if (ok) refresh() else _s.update { it.copy(message = "تعذر إعادة التسمية") }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val parent = repo.currentParentUri(_s.value.currentPath, _s.value.currentUri)
            if (parent == null) {
                _s.update { it.copy(message = "لا يوجد مجلد حالي") }
                return@launch
            }
            val uri = repo.createFolder(parent, name)
            if (uri == null) _s.update { it.copy(message = "تعذر إنشاء المجلد") }
            refresh()
        }
    }

    fun copySelected(items: List<FileItem>) = setClipboard(items, ClipOperation.COPY)

    fun cutSelected(items: List<FileItem>) = setClipboard(items, ClipOperation.CUT)

    private fun setClipboard(items: List<FileItem>, op: ClipOperation) {
        val src = repo.currentParentUri(_s.value.currentPath, _s.value.currentUri) ?: return
        if (items.isEmpty()) return
        ClipboardManager.set(items, op, src)
        _s.update { it.copy(clipboardCount = items.size, message = "تم النسخ إلى الحافظة") }
    }

    fun pasteHere() {
        val dest = repo.currentParentUri(_s.value.currentPath, _s.value.currentUri) ?: return
        val clip = ClipboardManager.get() ?: return
        FileOperationWorker.enqueue(
            getApplication(),
            if (clip.operation == ClipOperation.COPY) "copy" else "move",
            clip.items.map { it.uri },
            dest
        )
        if (clip.operation == ClipOperation.CUT) ClipboardManager.clear()
        _s.update {
            it.copy(
                clipboardCount = if (clip.operation == ClipOperation.CUT) 0 else it.clipboardCount,
                message = "بدأت عملية النقل/النسخ"
            )
        }
        refresh()
    }

    fun cancelClipboard() {
        ClipboardManager.clear()
        _s.update { it.copy(clipboardCount = 0) }
    }

    private fun sort(l: List<FileItem>, m: SortMode): List<FileItem> {
        val (d, f) = l.partition { it.isDirectory }
        val c = when (m) {
            SortMode.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.DATE -> compareByDescending<FileItem> { it.lastModified }
            SortMode.SIZE -> compareByDescending<FileItem> { it.size }
            SortMode.TYPE -> compareBy<FileItem> { it.extension }
        }
        return d.sortedWith(c) + f.sortedWith(c)
    }
}
