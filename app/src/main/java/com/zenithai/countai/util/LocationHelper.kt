package com.zenithai.countai.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Coarse location for history entries — ONLY used when the user enables it
 * in Settings (default OFF) and grants the permission. Returns a short
 * "lat,lng" string or null. No continuous tracking, no upload.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun lastKnownLabel(context: Context): String? {
        if (!Prefs.locationEnabled(context) || !hasPermission(context)) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            for (p in providers) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(p) ?: continue
                return String.format("%.4f,%.4f", loc.latitude, loc.longitude)
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
