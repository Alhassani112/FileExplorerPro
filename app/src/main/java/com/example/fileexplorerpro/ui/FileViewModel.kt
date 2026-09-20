package com.example.fileexplorerpro.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fileexplorerpro.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val backStack: List<Pair<Uri?, String?>> = emptyList()
)

class FileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileRepository(app)
    private val _s = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _s.asStateFlow()

    /** فتح مجلد بالمسار المباشر (File API) */
    fun openPath(path: String) {
        viewModelScope.launch {
            val prevUri = _s.value.currentUri
            val prevPath = _s.value.currentPath
            _s.value = _s.value.copy(loading = true, currentPath = path)
            val list = withContext(Dispatchers.IO) { listByPath(path, _s.value.showHidden) }
            val sorted = sort(list, _s.value.sortMode)
            _s.value = _s.value.copy(
                items = sorted,
                loading = false,
                currentUri = null,
                currentPath = path,
                backStack = if (prevPath != null && prevPath != path)
                    _s.value.backStack + (prevUri to prevPath) else _s.value.backStack
            )
        }
    }

    /** فتح مجلد عبر SAF URI */
    fun openUri(uri: Uri) {
        viewModelScope.launch {
            val prevUri = _s.value.currentUri
            val prevPath = _s.value.currentPath
            _s.value = _s.value.copy(loading = true, currentUri = uri, currentPath = null)
            val list = repo.listDirectory(uri, _s.value.showHidden)
            val sorted = sort(list, _s.value.sortMode)
            _s.value = _s.value.copy(
                items = sorted,
                loading = false,
                backStack = if (prevUri != null && prevUri != uri)
                    _s.value.backStack + (prevUri to prevPath) else _s.value.backStack
            )
        }
    }

    /** @deprecated استخدم openUri */
    fun openDirectory(uri: Uri, push: Boolean = true) = openUri(uri)

    fun goBack(): Boolean {
        val bs = _s.value.backStack
        if (bs.isEmpty()) return false
        val (prevUri, prevPath) = bs.last()
        _s.value = _s.value.copy(backStack = bs.dropLast(1))
        if (prevPath != null) openPath(prevPath) else prevUri?.let { openUri(it) }
        return true
    }

    fun toggleHidden() {
        _s.value = _s.value.copy(showHidden = !_s.value.showHidden)
        refresh()
    }

    fun toggleViewMode() {
        _s.value = _s.value.copy(
            viewMode = if (_s.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        )
    }

    fun setSort(m: SortMode) {
        _s.value = _s.value.copy(sortMode = m, items = sort(_s.value.items, m))
    }

    fun setSearch(q: String) { _s.value = _s.value.copy(searchQuery = q) }

    fun refresh() {
        _s.value.currentPath?.let { openPath(it) }
            ?: _s.value.currentUri?.let { openUri(it) }
    }

    fun delete(item: FileItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { File(item.uri.path ?: return@withContext false).delete() }
                catch (_: Exception) { false }
            }
            if (ok) _s.value = _s.value.copy(items = _s.value.items - item)
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val src = File(item.uri.path ?: return@withContext false)
                    src.renameTo(File(src.parentFile, newName))
                } catch (_: Exception) { false }
            }
            if (ok) refresh()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val base = _s.value.currentPath?.let { File(it) }
                        ?: _s.value.currentUri?.let { repo.uriToFile(it) }
                    base?.let { File(it, name).mkdirs() }
                } catch (_: Exception) {}
            }
            refresh()
        }
    }

    fun copySelected(items: List<FileItem>) { /* TODO */ }
    fun cutSelected(items: List<FileItem>) { /* TODO */ }
    fun pasteHere() { /* TODO */ }
    fun cancelClipboard() { }

    private fun listByPath(path: String, showHidden: Boolean): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                FileItem(
                    uri = Uri.fromFile(f),
                    name = f.name,
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) 0 else f.length(),
                    lastModified = f.lastModified(),
                    mimeType = null,
                    posterUri = if (f.isDirectory) PosterResolver.findPosterByPath(f) else null
                )
            }
            .toList()
    }

    private fun sort(l: List<FileItem>, m: SortMode): List<FileItem> {
        val (d, f) = l.partition { it.isDirectory }
        val c = when (m) {
            SortMode.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.DATE -> compareByDescending { it.lastModified }
            SortMode.SIZE -> compareByDescending { it.size }
            SortMode.TYPE -> compareBy { it.extension }
        }
        return d.sortedWith(c) + f.sortedWith(c)
    }
}
