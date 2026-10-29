package io.github.conor2803.coach.coach

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.conor2803.coach.Advice
import io.github.conor2803.coach.MainActivity
import io.github.conor2803.coach.R

object Notifier {
    private const val CHANNEL = "coach_checkins"
    private const val ID_ADVICE = 1
    private const val ID_ERROR = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            "Coach check-ins",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Your daily plan from the coach" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun advice(context: Context, advice: Advice) = post(context, ID_ADVICE, advice.headline, advice.plan)

    fun error(context: Context, message: String) =
        post(context, ID_ERROR, "Coach couldn't check in", message)

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
