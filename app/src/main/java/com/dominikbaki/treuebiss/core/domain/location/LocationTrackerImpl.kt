package com.dominikbaki.treuebiss.core.domain.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.dominikbaki.treuebiss.core.domain.location.LocationTracker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Konkrete Implementierung des LocationTrackers mit dem FusedLocationProviderClient
 * von Google Play Services.
 */
class LocationTrackerImpl @Inject constructor(
    private val locationClient: FusedLocationProviderClient,
    private val application: Application
) : LocationTracker {

    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getCurrentLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            application, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission || !isLocationEnabled()) {
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val cancellationTokenSource = CancellationTokenSource()
            locationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).apply {
                addOnSuccessListener { location -> cont.resume(location) }
                addOnFailureListener { cont.resume(null) }
                addOnCanceledListener { cont.cancel() }
            }
            cont.invokeOnCancellation { cancellationTokenSource.cancel() }
        }
    }

    /**
     * NEU: Implementierung der Funktion zur Prüfung des GPS-Status.
     */
    override fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}