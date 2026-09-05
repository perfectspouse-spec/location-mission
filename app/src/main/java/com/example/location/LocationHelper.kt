package com.example.location

import android.location.Location
import kotlin.math.roundToInt

object LocationHelper {

    /**
     * Calculates distance in meters between two lat/lng points
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Formats distance nicely for Turkish UI
     */
    fun formatDistance(meters: Float): String {
        return if (meters < 1000) {
            "${meters.roundToInt()} m"
        } else {
            val km = meters / 1000f
            String.format("%.1f km", km)
        }
    }
}
