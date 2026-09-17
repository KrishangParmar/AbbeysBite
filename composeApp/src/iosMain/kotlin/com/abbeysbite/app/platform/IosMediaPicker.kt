package com.abbeysbite.app.platform

import com.abbeysbite.app.core.config.AppConfig
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
    return result
}

/**
 * iOS media picker built on UIImagePickerController (camera + photo library)
 * with stable, retained delegates. Output is downscaled and JPEG-compressed
 * to the same limits as Android.
 */
class IosMediaPicker : MediaPicker {

    private var activeDelegate: NSObject? = null // retained while the picker is presented

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }

    override suspend fun cameraPermissionStatus(): PermissionStatus =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NOT_DETERMINED
            else -> PermissionStatus.DENIED
        }

    private suspend fun ensureCameraPermission(): Boolean =
        when (cameraPermissionStatus()) {
            PermissionStatus.GRANTED -> true
            PermissionStatus.NOT_DETERMINED -> suspendCancellableCoroutine { cont ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (cont.isActive) cont.resume(granted)
                }
            }
            else -> false
        }

    override suspend fun capturePhoto(): PickedImage? {
        if (!ensureCameraPermission()) return null
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
        ) return null
        return pick(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
    }

    override suspend fun pickPhoto(): PickedImage? =
        pick(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)

    private suspend fun pick(source: UIImagePickerControllerSourceType): PickedImage? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val picker = UIImagePickerController().apply {
                    sourceType = source
                }
                val delegate = object : NSObject(),
                    UIImagePickerControllerDelegateProtocol,
                    UINavigationControllerDelegateProtocol {

                    override fun imagePickerController(
                        picker: UIImagePickerController,
                        didFinishPickingMediaWithInfo: Map<Any?, *>,
                    ) {
                        val image =
                            didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                        picker.dismissViewControllerAnimated(true) {
                            activeDelegate = null
                            if (cont.isActive) cont.resume(image?.let(::compress))
                        }
                    }

                    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                        picker.dismissViewControllerAnimated(true) {
                            activeDelegate = null
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                activeDelegate = delegate
                picker.delegate = delegate
                val presenter = topViewController()
                if (presenter == null) {
                    activeDelegate = null
                    cont.resume(null)
                } else {
                    presenter.presentViewController(picker, animated = true, completion = null)
                }
                cont.invokeOnCancellation { activeDelegate = null }
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun compress(image: UIImage): PickedImage? {
        val maxDim = AppConfig.Ai.IMAGE_MAX_DIMENSION.toDouble()
        val (origW, origH) = image.size.useContents { width to height }
        if (origW <= 0.0 || origH <= 0.0) return null
        val scale = minOf(1.0, maxDim / origW, maxDim / origH)
        val targetW = origW * scale
        val targetH = origH * scale

        val finalImage = if (scale < 1.0) {
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
                this.scale = 1.0 // pixel-exact, not screen-scale multiplied
            }
            val renderer = UIGraphicsImageRenderer(CGSizeMake(targetW, targetH), format)
            renderer.imageWithActions {
                image.drawInRect(CGRectMake(0.0, 0.0, targetW, targetH))
            }
        } else image

        val data = UIImageJPEGRepresentation(
            finalImage,
            AppConfig.Ai.IMAGE_JPEG_QUALITY / 100.0,
        ) ?: return null
        return PickedImage(data.toByteArray(), targetW.toInt(), targetH.toInt())
    }
}
