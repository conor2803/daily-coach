package io.github.conor2803.coach.places

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Called by Google Play services when you arrive at or leave a saved place. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "arrived"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "left"
            else -> return
        }
        val at = System.currentTimeMillis()
        event.triggeringGeofences?.forEach { PlacesLog.append(context, it.requestId, type, at) }
    }
}
