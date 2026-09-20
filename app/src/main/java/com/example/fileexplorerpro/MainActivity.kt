package com.example.fileexplorerpro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fileexplorerpro.player.PlayerActivity
import com.example.fileexplorerpro.ui.*
import com.example.fileexplorerpro.ui.theme.FileExplorerTheme
import com.example.fileexplorerpro.viewer.ImageViewerActivity

class MainActivity : ComponentActivity() {
    private val vm: FileViewModel by viewModels()

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            vm.openUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileExplorerTheme {
                val ctx = LocalContext.current
                // حالات الصلاحيات
                var hasLegacyPerms by remember { mutableStateOf(hasStoragePermission()) }
                var hasAllFiles by remember { mutableStateOf(hasAllFilesAccess()) }

                // طلب صلاحيات وقت التشغيل (أندرويد 6-10)
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    hasLegacyPerms = result.values.all { it } || hasStoragePermission()
                }

                // عند بدء التطبيق: اطلب الصلاحيات
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        val needed = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED
                        ) needed += Manifest.permission.READ_EXTERNAL_STORAGE
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED
                        ) needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) needed += Manifest.permission.POST_NOTIFICATIONS
                        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
                    } else {
                        hasAllFiles = hasAllFilesAccess()
                    }
                }

                // إذا لا توجد صلاحيات كافية → اعرض شاشة طلب
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFiles) {
                    PermissionScreen(
                        onRequest = { openAllFilesSettings() },
                        onRefresh = { hasAllFiles = hasAllFilesAccess() }
                    )
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !hasLegacyPerms) {
                    PermissionScreen(
                        onRequest = {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ))
                        },
                        onRefresh = { hasLegacyPerms = hasStoragePermission() }
                    )
                } else {
                    // ✅ الصلاحيات متوفرة → اعرض التطبيق
                    AppNavHost(vm, openTree, ctx)
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    private fun openAllFilesSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onRefresh: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Folder, null,
                Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "صلاحية إدارة الملفات",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "لتتمكن من تصفح الملفات ونسخها وحذفها، يحتاج التطبيق إلى صلاحية الوصول الكامل للملفات.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "سيتم فتح إعدادات النظام. ابحث عن \"الوصول إلى كل الملفات\" أو \"إدارة الملفات\" وفعّلها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text("منح الصلاحية", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تحقّق بعد المنح", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    vm: FileViewModel,
    openTree: androidx.activity.result.ActivityResultLauncher<Uri?>,
    ctx: android.content.Context
) {
    val nav = rememberNavController()
    val bs by nav.currentBackStackEntryAsState()
    val route = bs?.destination?.route ?: "home"

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "home",
                    onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("الرئيسية") }
                )
                NavigationBarItem(
                    selected = route == "files",
                    onClick = { nav.navigate("files") { popUpTo("home") } },
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("الملفات") }
                )
                NavigationBarItem(
                    selected = route == "usb",
                    onClick = { nav.navigate("usb") { popUpTo("home") } },
                    icon = { Icon(Icons.Default.Usb, null) },
                    label = { Text("USB") }
                )
            }
        }
    ) { pad ->
        NavHost(nav, "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onOpenVolume = { vol ->
                        vm.openPath(vol.path)
                        nav.navigate("files") { popUpTo("home") }
                    },
                    onPickFolder = { openTree.launch(null) },
                    onOpenUsb = { nav.navigate("usb") { popUpTo("home") } }
                )
            }
            composable("files") {
                FileBrowserScreen(
                    vm = vm,
                    onOpenMedia = { item ->
                        val intent = if (item.isImage) {
                            Intent(ctx, ImageViewerActivity::class.java)
                        } else {
                            Intent(ctx, PlayerActivity::class.java)
                        }
                        intent.data = item.uri
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(intent)
                    }
                )
            }
            composable("usb") { UsbScreen() }
        }
    }
}
