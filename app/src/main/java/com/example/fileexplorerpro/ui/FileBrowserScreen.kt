package com.example.fileexplorerpro.ui
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.fileexplorerpro.data.FileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FileBrowserScreen(vm: FileViewModel, onOpenMedia: (FileItem) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<FileItem?>(null) }
    var info by remember { mutableStateOf<FileItem?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var delTarget by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    Scaffold(
        topBar = { TopAppBar(
            title = {
                if (s.isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(vm::clearSelection) { Icon(Icons.Default.Close, null) }
                        Text("${s.selectedItems.size}", fontWeight = FontWeight.Bold)
                    }
                } else if (search) TextField(s.searchQuery, vm::setSearch,
                    placeholder = { Text("\u0628\u062d\u062b...") }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent))
                else Text(s.currentUri?.lastPathSegment ?: "\u0627\u0644\u062a\u062e\u0632\u064a\u0646", maxLines = 1)
            },
            navigationIcon = {
                if (s.isSelectionMode) {}
                else if (search) IconButton({ search = false; vm.setSearch("") }) { Icon(Icons.Default.Close, null) }
                else if (s.backStack.isNotEmpty()) IconButton({ vm.goBack() }) { Icon(Icons.Default.ArrowBack, null) }
            },
            actions = {
                if (s.isSelectionMode) {
                    IconButton(vm::selectAll) { Icon(Icons.Default.SelectAll, null) }
                    IconButton({ delTarget = s.items.filter { it.uri.toString() in s.selectedItems } }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    IconButton({ vm.copySelected(emptyList()) }) { Icon(Icons.Default.ContentCopy, null) }
                    IconButton({ vm.cutSelected(emptyList()) }) { Icon(Icons.Default.ContentCut, null) }
                } else {
                    IconButton({ search = true }) { Icon(Icons.Default.Search, null) }
                    IconButton(vm::toggleHidden) { Icon(Icons.Default.Visibility, null) }
                    IconButton(vm::toggleViewMode) { Icon(
                        if (s.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView, null) }
                    var open by remember { mutableStateOf(false) }
                    IconButton({ open = true }) { Icon(Icons.Default.Sort, null) }
                    DropdownMenu(open, { open = false }) { SortMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = { vm.setSort(m); open = false }
                        ) } }
                } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) },
        floatingActionButton = {
            if (!s.isSelectionMode) FloatingActionButton({ newFolder = true }) { Icon(Icons.Default.CreateNewFolder, null) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.storageInfo != null && s.backStack.isEmpty() && !search) {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Column(Modifier.padding(12.dp)) {
                        val storage = s.storageInfo!!
                        val usedPct = if (storage.total > 0) storage.used.toFloat() / storage.total else 0f
                        LinearProgressIndicator(progress = { usedPct }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = when {
                                usedPct > 0.9f -> MaterialTheme.colorScheme.error
                                usedPct > 0.7f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            })
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("\u0645\u0633\u062a\u062e\u062f\u0645: ${humanSize(storage.used)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("\u0645\u062c\u0627\u0646\u064a: ${humanSize(storage.free)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("\u0627\u0644\u0625\u062c\u0645\u0627\u0644\u064a: ${humanSize(storage.total)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (s.clipboardCount > 0) Surface(Modifier.fillMaxWidth().padding(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.isCutClipboard) Icons.Default.ContentCut else Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("${s.clipboardCount} \u0641\u064a \u0627\u0644\u062d\u0627\u0641\u0638\u0629", Modifier.weight(1f))
                    TextButton(vm::pasteHere) { Text("\u0644\u0635\u0642") }
                    TextButton(vm::cancelClipboard) { Text("\u0625\u0644\u063a\u0627\u0621") } } }
            Box(Modifier.fillMaxSize()) {
                if (s.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else {
                    val filtered = remember(s.items, s.searchQuery) {
                        if (s.searchQuery.isBlank()) s.items
                        else s.items.filter { it.name.contains(s.searchQuery, true) } }
                    val click: (FileItem) -> Unit = { it ->
                        if (s.isSelectionMode) vm.toggleSelection(it.uri.toString())
                        else when {
                            it.isDirectory -> vm.openDirectory(it.uri)
                            it.isPlayable || it.isImage -> onOpenMedia(it)
                        } }
                    val longClick: (FileItem) -> Unit = { it ->
                        if (!s.isSelectionMode) vm.toggleSelection(it.uri.toString()) }
                    if (s.viewMode == ViewMode.GRID) LazyVerticalGrid(GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.uri.toString() }) { i ->
                            val selected = i.uri.toString() in s.selectedItems
                            GridItem(i, selected, { click(i) }, { longClick(i) },
                                { vm.copySelected(listOf(i)) }, { vm.cutSelected(listOf(i)) },
                                { rename = i }, { info = i }, { delTarget = listOf(i) }) } }
                    else LazyColumn { items(filtered, key = { it.uri.toString() }) { i ->
                        val selected = i.uri.toString() in s.selectedItems
                        ListRow(i, selected, { click(i) }, { longClick(i) },
                            { vm.copySelected(listOf(i)) }, { vm.cutSelected(listOf(i)) },
                            { rename = i }, { info = i }, { delTarget = listOf(i) }) } }
                } } }
        rename?.let { RenameDialog(it, { rename = null }, { n -> vm.rename(it, n) }) }
        info?.let { FileInfoDialog(it) { info = null } }
        if (newFolder) CreateFolderDialog({ newFolder = false }, { vm.createFolder(it) })
        if (delTarget.isNotEmpty()) DeleteConfirmDialog(delTarget.size,
            { delTarget = emptyList() }, { delTarget.forEach { vm.delete(it) } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun GridItem(i: FileItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onCopy: () -> Unit, onCut: () -> Unit, onRename: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit) {
    var m by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(10.dp))
        .background(
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            if (i.posterUri != null) AsyncImage(i.posterUri, i.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(if (i.isDirectory) Icons.Default.Folder else iconFor(i), null,
                Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.TopEnd) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp).size(20.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(i.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!i.isDirectory && i.size > 0) Text(humanSize(i.size), fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ItemMenu(m, { m = false }, onCopy, onCut, onRename, onInfo, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ListRow(i: FileItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onCopy: () -> Unit, onCut: () -> Unit, onRename: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit) {
    var m by remember { mutableStateOf(false) }
    Column {
        ListItem(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            leadingContent = {
                if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                else if (i.posterUri != null)
                    AsyncImage(i.posterUri, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                else Icon(iconFor(i), null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(i.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(if (i.isDirectory) "\u0645\u062c\u0644\u062f" else humanSize(i.size), fontSize = 12.sp) },
            trailingContent = {
                if (!i.isDirectory && i.isPlayable) Icon(Icons.Default.PlayCircle, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                else if (i.isImage) Icon(Icons.Default.Image, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            })
        ItemMenu(m, { m = false }, onCopy, onCut, onRename, onInfo, onDelete)
        HorizontalDivider()
    }
}

@Composable fun ItemMenu(expanded: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit,
    onRename: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("\u0646\u0633\u062e") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
            onClick = { onDismiss(); onCopy() })
        DropdownMenuItem(text = { Text("\u0642\u0635") },
            leadingIcon = { Icon(Icons.Default.ContentCut, null) },
            onClick = { onDismiss(); onCut() })
        DropdownMenuItem(text = { Text("\u0625\u0639\u0627\u062f\u0629 \u062a\u0633\u0645\u064a\u0629") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = { onDismiss(); onRename() })
        DropdownMenuItem(text = { Text("\u0645\u0639\u0644\u0648\u0645\u0627\u062a") },
            leadingIcon = { Icon(Icons.Default.Info, null) },
            onClick = { onDismiss(); onInfo() })
        DropdownMenuItem(text = { Text("\u062d\u0630\u0641", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }) }
}

private fun iconFor(i: FileItem) = when {
    i.isDirectory -> Icons.Default.Folder
    i.isVideo -> Icons.Default.Movie
    i.isAudio -> Icons.Default.MusicNote
    i.isImage -> Icons.Default.Image
    i.isSubtitle -> Icons.Default.Subtitles
    i.isDocument -> Icons.Default.Description
    i.isArchive -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile }
