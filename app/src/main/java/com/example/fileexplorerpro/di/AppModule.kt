package com.example.fileexplorerpro.di

import android.content.Context
import com.example.fileexplorerpro.data.FileRepository
import com.example.fileexplorerpro.usb.UsbStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun fileRepository(@ApplicationContext ctx: Context): FileRepository = FileRepository(ctx)

    @Provides
    @Singleton
    fun usbStorageManager(@ApplicationContext ctx: Context): UsbStorageManager = UsbStorageManager(ctx)
}
