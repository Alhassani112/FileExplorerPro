package com.example.fileexplorerpro.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.fileexplorerpro.data.FileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(vm: FileViewModel, onOpenMedia: (FileItem) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<FileItem?>(null) }
    var info by remember { mutableStateOf<FileItem?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var delTarget by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    val title = s.currentPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: s.currentUri?.lastPathSegment
        ?: "الملفات"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (search) {
                        TextField(
                            value = s.searchQuery,
                            onValueChange = vm::setSearch,
                            placeholder = { Text("بحث...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (search) {
                        IconButton({ search = false; vm.setSearch("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    } else if (s.backStack.isNotEmpty()) {
                        IconButton({ vm.goBack() }) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    }
                },
                actions = {
                    IconButton({ search = true }) { Icon(Icons.Default.Search, null) }
                    IconButton(vm::toggleHidden) {
                        Icon(
                            if (s.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                    IconButton(vm::toggleViewMode) {
                        Icon(
                            if (s.viewMode == ViewMode.GRID) Icons.Default.ViewList
                            else Icons.Default.GridView, null
                        )
                    }
                    var open by remember { mutableStateOf(false) }
                    IconButton({ open = true }) { Icon(Icons.Default.Sort, null) }
                    DropdownMenu(open, { open = false }) {
                        SortMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = { vm.setSort(m); open = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton({ newFolder = true }) {
                Icon(Icons.Default.CreateNewFolder, null)
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (s.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val filtered = remember(s.items, s.searchQuery) {
                    if (s.searchQuery.isBlank()) s.items
                    else s.items.filter { it.name.contains(s.searchQuery, true) }
                }

                if (filtered.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOpen, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("المجلد فارغ", fontSize = 16.sp)
                    }
                } else {
                    val click: (FileItem) -> Unit = { item ->
                        if (item.isDirectory) {
                            val p = item.uri.path
                            if (p != null) vm.openPath(p)
                        } else if (item.isPlayable || item.isImage) {
                            onOpenMedia(item)
                        }
                    }
                    if (s.viewMode == ViewMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.uri.toString() }) { i ->
                                GridItem(i, { click(i) },
                                    { rename = i }, { info = i }, { delTarget = listOf(i) })
                            }
                        }
                    } else {
                        LazyColumn {
                            items(filtered, key = { it.uri.toString() }) { i ->
                                ListRow(i, { click(i) },
                                    { rename = i }, { info = i }, { delTarget = listOf(i) })
                            }
                        }
                    }
                }
            }
        }

        rename?.let {
            RenameDialog(it, { rename = null }, { n -> vm.rename(it, n) })
        }
        info?.let { FileInfoDialog(it) { info = null } }
        if (newFolder) {
            CreateFolderDialog({ newFolder = false }, { vm.createFolder(it) })
        }
        if (delTarget.isNotEmpty()) {
            DeleteConfirmDialog(
                delTarget.size,
                { delTarget = emptyList() },
                { delTarget.forEach { vm.delete(it) } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridItem(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var m by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(onClick = onClick, onLongClick = { m = true })
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.posterUri != null) {
                AsyncImage(
                    item.posterUri, item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    iconFor(item), null,
                    Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ItemMenu(m, { m = false }, onRename, onInfo, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var m by remember { mutableStateOf(false) }
    Column {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { m = true }
            ),
            leadingContent = {
                if (item.posterUri != null) {
                    AsyncImage(
                        item.posterUri, null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Icon(iconFor(item), null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            headlineContent = {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    if (item.isDirectory) "مجلد" else humanSize(item.size),
                    fontSize = 12.sp
                )
            }
        )
        ItemMenu(m, { m = false }, onRename, onInfo, onDelete)
        HorizontalDivider()
    }
}

@Composable
fun ItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("إعادة تسمية") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("معلومات") },
            leadingIcon = { Icon(Icons.Default.Info, null) },
            onClick = { onDismiss(); onInfo() }
        )
        DropdownMenuItem(
            text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

private fun iconFor(i: FileItem) = when {
    i.isDirectory -> Icons.Default.Folder
    i.isVideo -> Icons.Default.Movie
    i.isAudio -> Icons.Default.MusicNote
    i.isImage -> Icons.Default.Image
    else -> Icons.Default.InsertDriveFile
}
