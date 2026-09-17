package com.abbeysbite.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.abbeysbite.app.platform.ActivityResultBridge
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity(), ActivityResultBridge.Host {

    private var pendingCaptureUri: Uri? = null
    private var captureContinuation: ((Uri?) -> Unit)? = null
    private var pickContinuation: ((Uri?) -> Unit)? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            captureContinuation?.invoke(if (success) pendingCaptureUri else null)
            captureContinuation = null
        }

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pickContinuation?.invoke(uri)
            pickContinuation = null
        }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionContinuation?.invoke(granted)
            permissionContinuation = null
        }

    private val supabase: SupabaseClient by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ActivityResultBridge.host = this
        handleAppLink(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAppLink(intent)
    }

    private fun handleAppLink(intent: Intent?) {
        val uri = intent?.data
        if (uri?.scheme == "abbeysbite" && uri.host != "auth-callback") {
            // Notification taps → in-app navigation.
            com.abbeysbite.app.navigation.DeepLinkBus.handleUri(uri.host, uri.path)
        } else {
            // Completes email-confirmation / recovery flows via
            // abbeysbite://auth-callback (sessionStatus flips to Authenticated).
            runCatching { supabase.handleDeeplinks(intent ?: return) }
        }
    }

    override fun onDestroy() {
        if (ActivityResultBridge.host === this) ActivityResultBridge.host = null
        super.onDestroy()
    }

    override suspend fun capturePhoto(): Uri? = suspendCancellableCoroutine { cont ->
        val dir = File(cacheDir, "camera_captures").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCaptureUri = uri
        captureContinuation = { result -> if (cont.isActive) cont.resume(result) }
        cont.invokeOnCancellation { captureContinuation = null }
        takePicture.launch(uri)
    }

    override suspend fun pickPhoto(): Uri? = suspendCancellableCoroutine { cont ->
        pickContinuation = { result -> if (cont.isActive) cont.resume(result) }
        cont.invokeOnCancellation { pickContinuation = null }
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private suspend fun ensurePermission(permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return true
        return suspendCancellableCoroutine { cont ->
            permissionContinuation = { granted -> if (cont.isActive) cont.resume(granted) }
            cont.invokeOnCancellation { permissionContinuation = null }
            requestPermission.launch(permission)
        }
    }

    override suspend fun ensureCameraPermission(): Boolean =
        ensurePermission(Manifest.permission.CAMERA)

    override suspend fun ensureMicPermission(): Boolean =
        ensurePermission(Manifest.permission.RECORD_AUDIO)

    override suspend fun ensureNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ensurePermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
}
