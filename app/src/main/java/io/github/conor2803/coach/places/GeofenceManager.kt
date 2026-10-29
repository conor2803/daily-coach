package io.github.conor2803.coach.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.conor2803.coach.Prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object GeofenceManager {

    fun hasForegroundLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context) = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /**
     * Replaces all geofences with the saved places. Safe to call any time.
     * Returns a problem to show the user, or null if all is well.
     */
    @SuppressLint("MissingPermission")
    fun register(context: Context): String? {
        val places = Prefs(context).places
        val client = LocationServices.getGeofencingClient(context)
        val pending = pendingIntent(context)

        if (places.isEmpty()) {
            client.removeGeofences(pending)
            return null
        }
        if (!hasForegroundLocation(context) || !hasBackgroundLocation(context)) {
            return "Location needs \"Allow all the time\" for places to work"
        }

        val fences = places.map {
            Geofence.Builder()
                .setRequestId(it.name)
                .setCircularRegion(it.lat, it.lng, it.radiusM)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()

        client.removeGeofences(pending).addOnCompleteListener {
            client.addGeofences(request, pending)
        }
        return null
    }

    /** One fresh GPS fix, used when saving "I'm at the office now". */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasForegroundLocation(context)) return null
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    /** Last known position without waking GPS. Used for the weather in the background. */
    @SuppressLint("MissingPermission")
    suspend fun lastLocation(context: Context): Location? {
        if (!hasForegroundLocation(context)) return null
        return suspendCancellableCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java),
        // Must be mutable so Play services can attach the geofence event.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
