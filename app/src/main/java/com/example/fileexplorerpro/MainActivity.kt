package com.example.fileexplorerpro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        requestStorageAccessIfNeeded()

        setContent {
            FileExplorerTheme {
                val ctx = LocalContext.current
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
        }
    }

    private fun requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
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
}
