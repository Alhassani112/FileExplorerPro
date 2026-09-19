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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import com.example.fileexplorerpro.player.PlayerActivity
import com.example.fileexplorerpro.ui.*
import com.example.fileexplorerpro.ui.theme.FileExplorerTheme
import com.example.fileexplorerpro.viewer.ImageViewerActivity
class MainActivity : ComponentActivity() {
    private val vm: FileViewModel by viewModels()
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.openDirectory(it) } }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:$packageName"))) } catch (_: Exception) {} }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) } }
        setContent { FileExplorerTheme {
            val ctx = LocalContext.current
            val nav = rememberNavController()
            val bs by nav.currentBackStackEntryAsState()
            val route = bs?.destination?.route ?: "files"
            Scaffold(bottomBar = { NavigationBar {
                NavigationBarItem(route == "files", { nav.navigate("files") { popUpTo("files") { inclusive = true } } },
                    { Icon(Icons.Default.Folder, null) }, label = { Text("\u0627\u0644\u0645\u0644\u0641\u0627\u062a") })
                NavigationBarItem(route == "usb", { nav.navigate("usb") { popUpTo("files") } },
                    { Icon(Icons.Default.Usb, null) }, label = { Text("USB") }) } }
            ) { pad -> NavHost(nav, "files", modifier = Modifier.padding(pad)) {
                composable("files") { FileBrowserScreen(vm) { item ->
                    val i = if (item.isImage) Intent(ctx, ImageViewerActivity::class.java)
                    else Intent(ctx, PlayerActivity::class.java)
                    i.data = item.uri; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(i) } }
                composable("usb") { UsbScreen() } } }
            LaunchedEffect(Unit) { if (vm.state.value.currentUri == null) openTree.launch(null) }
        } }
    }
}
