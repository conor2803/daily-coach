package io.github.conor2803.coach.context

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads events from every calendar on the phone (Google, Outlook, etc.). */
object CalendarReader {
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val dayOnly = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun events(context: Context, from: Long, to: Long): String {
        if (!hasPermission(context)) return "calendar permission not granted"
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val zone = ZoneId.systemDefault()
        val lines = mutableListOf<String>()
        context.contentResolver.query(
            uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && lines.size < 25) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)"
                val begin = Instant.ofEpochMilli(c.getLong(1))
                val end = Instant.ofEpochMilli(c.getLong(2))
                val allDay = c.getInt(3) == 1
                lines += if (allDay) {
                    // All-day events are stored at UTC midnight.
                    "${begin.atZone(ZoneOffset.UTC).format(dayOnly)} (all day): $title"
                } else {
                    "${begin.atZone(zone).format(dayTime)}–${end.atZone(zone).format(time)}: $title"
                }
            }
        }
        return if (lines.isEmpty()) "nothing on" else lines.joinToString("\n  ", prefix = "\n  ")
    }
}
