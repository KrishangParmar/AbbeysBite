package com.abbeysbite.app.platform

import android.net.Uri

/**
 * Bridge between the DI-scoped [AndroidMediaPicker] and the Activity that owns
 * the ActivityResult launchers. MainActivity registers itself on create and
 * clears on destroy.
 */
object ActivityResultBridge {
    interface Host {
        /** Launches the camera; resumes with the captured photo Uri or null. */
        suspend fun capturePhoto(): Uri?

        /** Launches the photo picker; resumes with the picked Uri or null. */
        suspend fun pickPhoto(): Uri?

        /** Requests CAMERA runtime permission if needed; true when granted. */
        suspend fun ensureCameraPermission(): Boolean

        /** Requests RECORD_AUDIO permission if needed; true when granted. */
        suspend fun ensureMicPermission(): Boolean

        /** Requests POST_NOTIFICATIONS permission (API 33+); true when granted. */
        suspend fun ensureNotificationPermission(): Boolean
    }

    @Volatile
    var host: Host? = null
}
