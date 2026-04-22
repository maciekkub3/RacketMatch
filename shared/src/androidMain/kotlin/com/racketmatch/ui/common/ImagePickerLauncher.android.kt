package com.racketmatch.ui.common

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

actual class ImagePickerLauncher(
    private val onGallery: () -> Unit,
    private val onCamera: () -> Unit,
    private val requestCameraPermission: () -> Unit
) {
    actual fun launchGallery() = onGallery()
    actual fun launchCamera() = requestCameraPermission()
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val onImageRef = rememberUpdatedState(onImage)

    // Camera: FileProvider URI into app cache
    val cameraImageUri = remember {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        val file = File(dir, "photo.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun Uri.toJpegBytes(): ByteArray? =
        context.contentResolver.openInputStream(this)?.use { stream ->
            val raw = BitmapFactory.decodeStream(stream) ?: return@use null
            // Hardware bitmaps can't be compressed directly — copy to software first
            val bitmap = if (raw.config == Bitmap.Config.HARDWARE) {
                raw.copy(Bitmap.Config.ARGB_8888, false)
            } else raw
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        uri.toJpegBytes()?.let { onImageRef.value(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri.toJpegBytes()?.let { onImageRef.value(it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(cameraImageUri)
    }

    return remember(galleryLauncher, cameraLauncher, cameraPermissionLauncher) {
        ImagePickerLauncher(
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = { cameraLauncher.launch(cameraImageUri) },
            requestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}
