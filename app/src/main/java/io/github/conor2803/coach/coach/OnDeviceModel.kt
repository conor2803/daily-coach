package io.github.conor2803.coach.coach

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import io.github.conor2803.coach.Prefs
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val percent: Int?) : ModelState
    data object Ready : ModelState
    data class Failed(val reason: Int) : ModelState
}

/** The Gemma model file the on-device engine runs, fetched once by Android's download manager. */
object OnDeviceModel {
    const val NAME = "Gemma 4 E2B"
    const val SIZE = "2.6 GB"
    private const val FILE = "gemma-4-E2B-it.litertlm"
    private const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE"

    fun file(context: Context): File = File(context.getExternalFilesDir(null), FILE)

    fun isDownloaded(context: Context): Boolean = state(context) == ModelState.Ready

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

    fun state(context: Context): ModelState {
        val prefs = Prefs(context)
        val id = prefs.modelDownloadId
        if (id == NO_DOWNLOAD) return if (file(context).exists()) ModelState.Ready else ModelState.Missing

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
                    if (file(context).exists()) ModelState.Ready else ModelState.Missing
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.modelDownloadId = NO_DOWNLOAD
                    file(context).delete()
                    ModelState.Failed(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                }
                else -> {
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    ModelState.Downloading(if (total > 0) (done * 100 / total).toInt() else null)
                }
            }
        }
    }

    private fun downloads(context: Context) = context.getSystemService(DownloadManager::class.java)

    const val NO_DOWNLOAD = -1L
}
