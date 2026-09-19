package com.example.fileexplorerpro.ui
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fileexplorerpro.data.FileItem
@Composable fun RenameDialog(item: FileItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(item.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("\u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u062a\u0633\u0645\u064a\u0629") },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("\u0627\u0644\u0627\u0633\u0645 \u0627\u0644\u062c\u062f\u064a\u062f") }) },
        confirmButton = { TextButton({ if (t.isNotBlank() && t != item.name) onConfirm(t); onDismiss() }) { Text("\u062d\u0641\u0638") } },
        dismissButton = { TextButton(onDismiss) { Text("\u0625\u0644\u063a\u0627\u0621") } }) }
@Composable fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("\u0645\u062c\u0644\u062f \u062c\u062f\u064a\u062f") },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("\u0627\u0633\u0645 \u0627\u0644\u0645\u062c\u0644\u062f") }) },
        confirmButton = { TextButton({ if (t.isNotBlank()) onConfirm(t.trim()); onDismiss() }) { Text("\u0625\u0646\u0634\u0627\u0621") } },
        dismissButton = { TextButton(onDismiss) { Text("\u0625\u0644\u063a\u0627\u0621") } }) }
@Composable fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("\u062a\u0623\u0643\u064a\u062f \u0627\u0644\u062d\u0630\u0641") },
        text = { Text(if (count == 1) "\u062d\u0630\u0641 \u0647\u0630\u0627 \u0627\u0644\u0639\u0646\u0635\u0631\u061f" else "\u062d\u0630\u0641 $count \u0639\u0646\u0627\u0635\u0631\u061f") },
        confirmButton = { TextButton({ onConfirm(); onDismiss() },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("\u062d\u0630\u0641") } },
        dismissButton = { TextButton(onDismiss) { Text("\u0625\u0644\u063a\u0627\u0621") } }) }
@Composable fun FileInfoDialog(item: FileItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("\u0645\u0639\u0644\u0648\u0645\u0627\u062a") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("\u0627\u0644\u0627\u0633\u0645", item.name)
            InfoRow("\u0627\u0644\u0646\u0648\u0639", when {
                item.isDirectory -> "\u0645\u062c\u0644\u062f"
                item.isVideo -> "\u0641\u064a\u062f\u064a\u0648"
                item.isAudio -> "\u0635\u0648\u062a"
                item.isImage -> "\u0635\u0648\u0631\u0629"
                item.isSubtitle -> "\u062a\u0631\u062c\u0645\u0629"
                item.isDocument -> "\u0645\u0633\u062a\u0646\u062f\u064b"
                item.isArchive -> "\u0623\u0631\u0634\u064a\u0641 \u0645\u0636\u064a\u0645"
                else -> item.mimeType ?: "\u0645\u0644\u0641"
            })
            if (!item.isDirectory) InfoRow("\u0627\u0644\u062d\u062c\u0645", humanSize(item.size))
            InfoRow("\u0627\u0644\u0627\u0645\u062a\u062f\u0627\u0639", item.extension.ifEmpty { "\u063a\u064a\u0631 \u0645\u062d\u062f\u062f" })
            if (item.lastModified > 0) InfoRow("\u0622\u062e\u0631 \u062a\u0639\u062f\u064a\u0644",
                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(item.lastModified)))
            if (!item.isDirectory) {
                val path = item.uri.path ?: ""
                if (path.isNotEmpty()) InfoRow("\u0627\u0644\u0645\u0633\u0627\u0631", path)
            }
        } },
        confirmButton = {
            Row {
                if (!item.isDirectory) IconButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, item.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(sendIntent, "\u0645\u0634\u0627\u0631\u0643\u0629"))
                }) { Icon(Icons.Default.Share, null) }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("\u0625\u063a\u0644\u0627\u0642") }
            }
        }) }
@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp)
    }
}
fun humanSize(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0; if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0; if (m < 1024) return "%.1f MB".format(m)
    return "%.2f GB".format(m / 1024.0) }
