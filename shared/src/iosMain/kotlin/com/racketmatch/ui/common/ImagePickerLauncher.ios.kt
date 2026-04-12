package com.racketmatch.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

actual class ImagePickerLauncher(private val onImage: (ByteArray) -> Unit) {
    private var delegate: NSObject? = null

    actual fun launchGallery() {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        val picker = PHPickerViewController(config)
        val d = GalleryDelegate(onImage) { delegate = null }
        delegate = d
        picker.delegate = d
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    actual fun launchCamera() {
        val picker = UIImagePickerController()
        val d = CameraDelegate(onImage) { delegate = null }
        delegate = d
        picker.delegate = d
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberImagePickerLauncher(onImage: (ByteArray) -> Unit): ImagePickerLauncher =
    remember(onImage) { ImagePickerLauncher(onImage) }

private fun topViewController(): UIViewController? =
    UIApplication.sharedApplication.keyWindow?.rootViewController

private class GalleryDelegate(
    private val onImage: (ByteArray) -> Unit,
    private val onDone: () -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: run { onDone(); return }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            data?.let { onImage(it.toByteArray()) }
            onDone()
        }
    }
}

private class CameraDelegate(
    private val onImage: (ByteArray) -> Unit,
    private val onDone: () -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        image?.let {
            UIImageJPEGRepresentation(it, 0.8)?.let { data -> onImage(data.toByteArray()) }
        }
        onDone()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    return ByteArray(len).also { arr ->
        arr.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
