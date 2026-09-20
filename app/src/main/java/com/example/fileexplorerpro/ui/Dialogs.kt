package com.example.fileexplorerpro.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fileexplorerpro.R
import com.example.fileexplorerpro.data.FileItem
import com.example.fileexplorerpro.data.ShareHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RenameDialog(item: FileItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rename)) }
            )
        },
        confirmButton = {
            TextButton({
                if (t.isNotBlank() && t != item.name) onConfirm(t)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            OutlinedTextField(
                t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.new_folder)) }
            )
        },
        confirmButton = {
            TextButton({
                if (t.isNotBlank()) onConfirm(t.trim())
                onDismiss()
            }) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = { Text(if (count == 1) "حذف هذا العنصر؟" else "حذف $count عناصر؟") },
        confirmButton = {
            TextButton(
                { onConfirm(); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun FileInfoDialog(item: FileItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("الاسم", item.name)
                InfoRow(
                    "النوع",
                    when {
                        item.isDirectory -> "مجلد"
                        item.isVideo -> "فيديو"
                        item.isAudio -> "صوت"
                        item.isImage -> "صورة"
                        item.isSubtitle -> "ترجمة"
                        item.isDocument -> "مستند"
                        item.isArchive -> "أرشيف مضغوط"
                        else -> item.mimeType ?: "ملف"
                    }
                )
                if (!item.isDirectory) InfoRow("الحجم", humanSize(item.size))
                InfoRow("الامتداد", item.extension.ifEmpty { "غير محدد" })
                if (item.lastModified > 0) {
                    InfoRow(
                        "آخر تعديل",
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            .format(Date(item.lastModified))
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (!item.isDirectory) {
                    IconButton(onClick = {
                        scope.launch {
                            val intent = ShareHelper.shareIntent(ctx, item)
                            if (intent != null) {
                                ctx.startActivity(
                                    android.content.Intent.createChooser(
                                        intent,
                                        ctx.getString(R.string.share)
                                    )
                                )
                            } else {
                                Toast.makeText(ctx, "تعذر المشاركة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, stringResource(R.string.share))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onDismiss) { Text("إغلاق") }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp)
    }
}

fun humanSize(b: Long): String {
    if (b < 1024) return "$b B"
    val k = b / 1024.0
    if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0
    if (m < 1024) return "%.1f MB".format(m)
    return "%.2f GB".format(m / 1024.0)
}
