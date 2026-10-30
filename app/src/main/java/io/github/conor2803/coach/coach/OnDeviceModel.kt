package io.github.conor2803.coach.coach

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import io.github.conor2803.coach.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val percent: Int?) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/** The Gemma model file the on-device engine runs, fetched once by Android's download manager. */
object OnDeviceModel {
    const val NAME = "Gemma 4 E2B"
    const val SIZE = "2.6 GB"
    private const val FILE = "gemma-4-E2B-it.litertlm"
    private const val REVISION = "1caa527c1e88b55f046fcd8d2b4265606680663d"
    private const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    private const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$REVISION/$FILE"

    fun file(context: Context): File = File(context.getExternalFilesDir(null), FILE)

    fun download(context: Context) {
        file(context).delete()
        val request = DownloadManager.Request(Uri.parse(URL))
            .setTitle("Coach: on-device model")
            .setDescription("$NAME, $SIZE")
            .setDestinationInExternalFilesDir(context, null, FILE)
            .setAllowedOverMetered(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        Prefs(context).modelDownloadId = downloads(context).enqueue(request)
    }

    /** Cheap to call: never hashes. A finished download reads as [ModelState.Verifying] until [verify] runs. */
    fun state(context: Context): ModelState {
        val prefs = Prefs(context)
        val id = prefs.modelDownloadId
        if (id == NO_DOWNLOAD) {
            val file = file(context)
            return when {
                !file.exists() -> ModelState.Missing
                prefs.modelVerified == stamp(file) -> ModelState.Ready
                else -> ModelState.Verifying
            }
        }

        downloads(context).query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) {
                // Cancelled from the notification: forget it and any partial file.
                prefs.modelDownloadId = NO_DOWNLOAD
                file(context).delete()
                return ModelState.Missing
            }
            return when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.modelDownloadId = NO_DOWNLOAD
                    if (file(context).exists()) ModelState.Verifying else ModelState.Missing
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.modelDownloadId = NO_DOWNLOAD
                    file(context).delete()
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    ModelState.Failed("Download failed (code $reason). Try again.")
                }
                else -> {
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    ModelState.Downloading(if (total > 0) (done * 100 / total).toInt() else null)
                }
            }
        }
    }

    /** Hashes the whole file against the pinned SHA-256; a mismatch deletes it. */
    suspend fun verify(context: Context): ModelState = withContext(Dispatchers.IO) {
        val file = file(context)
        val prefs = Prefs(context)
        if (!file.exists()) return@withContext ModelState.Missing
        if (file.inputStream().use(::sha256Hex) == SHA256) {
            prefs.modelVerified = stamp(file)
            ModelState.Ready
        } else {
            file.delete()
            prefs.modelVerified = ""
            ModelState.Failed("The downloaded model didn't match the expected file, so it was deleted. Try again.")
        }
    }

    /** True once the model is downloaded and verified, verifying it first if that hasn't happened yet. */
    suspend fun isReady(context: Context): Boolean = when (state(context)) {
        ModelState.Ready -> true
        ModelState.Verifying -> verify(context) == ModelState.Ready
        else -> false
    }

    /** Ties a verification to this exact file, so a replaced file has to be verified again. */
    private fun stamp(file: File) = "$SHA256:${file.length()}:${file.lastModified()}"

    private fun downloads(context: Context) = context.getSystemService(DownloadManager::class.java)

    const val NO_DOWNLOAD = -1L
}
