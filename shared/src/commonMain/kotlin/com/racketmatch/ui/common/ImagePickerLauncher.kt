package com.racketmatch.ui.common

import androidx.compose.runtime.Composable

expect class ImagePickerLauncher {
    fun launchGallery()
    fun launchCamera()
}

@Composable
expect fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher
