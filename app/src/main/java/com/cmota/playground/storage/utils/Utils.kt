package com.cmota.playground.storage.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.cmota.playground.storage.R
import java.io.File

val FILTERS = listOf(R.string.filter_images,
    R.string.filter_videos,
    R.string.filter_starred,
    R.string.filter_trash)

fun hasStoragePermission(context: Context): Boolean {
    if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

fun getSettingsIntent(packageName: String): Intent{
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return intent
}

@Suppress("deprecation")
fun sendScanFileBroadcast(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
    intent.data = Uri.fromFile(file)
    context.sendBroadcast(intent)
}

fun hasSdkHigherThan(sdk: Int): Boolean {
    return Build.VERSION.SDK_INT > sdk
}