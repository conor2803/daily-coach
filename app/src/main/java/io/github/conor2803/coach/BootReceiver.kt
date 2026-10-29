package io.github.conor2803.coach

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.conor2803.coach.coach.Scheduler
import io.github.conor2803.coach.places.GeofenceManager

/** Geofences are wiped on reboot and on app update, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                GeofenceManager.register(context)
                Scheduler.ensureScheduled(context)
            }
        }
    }
}
