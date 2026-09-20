package com.example.fileexplorerpro.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.UsbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class UsbStorageManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var root: UsbFile? = null

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.fileexplorerpro.USB_PERMISSION"
    }

    /** قائمة أجهزة USB Mass Storage */
    fun listDevices(): List<UsbMassStorageDevice> = try {
        UsbMassStorageDevice.getMassStorageDevices(context).toList()
    } catch (_: Exception) { emptyList() }

    /** هل لدينا صلاحية لهذا الجهاز؟ */
    fun hasPermission(device: UsbMassStorageDevice): Boolean {
        return usbManager.hasPermission(device.usbDevice)
    }

    /** طلب صلاحية الوصول لجهاز USB */
    suspend fun requestPermission(device: UsbMassStorageDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        val granted = intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false
                        )
                        if (cont.isActive) cont.resume(granted)
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usbManager.requestPermission(device.usbDevice, pi)
            cont.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        }

    /** فتح الجهاز بعد الحصول على الصلاحية */
    suspend fun open(device: UsbMassStorageDevice): Result<UsbFile> =
        withContext(Dispatchers.IO) {
            try {
                device.init()
                val partitions = device.partitions
                if (partitions.isEmpty()) {
                    return@withContext Result.failure(Exception("لا توجد أقسام"))
                }
                val partition = partitions[0]
                val fs = partition.fileSystem
                root = fs.rootDirectory
                Result.success(root!!)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun currentRoot(): UsbFile? = root

    fun listChildren(dir: UsbFile): List<UsbFile> = try {
        dir.listFiles().toList()
    } catch (_: Exception) { emptyList() }
}
