package com.example.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object MapIntentHelper {

    private const val TAG = "MapIntentHelper"

    /**
     * Opens the Google Maps application (or browser fallback) centered directly
     * on the given coordinates with a prominent location pin and label showing the entered place name.
     */
    fun openLocationInGoogleMaps(
        context: Context,
        latitude: Double,
        longitude: Double,
        placeName: String = ""
    ) {
        val cleanPlaceName = placeName.trim()
        val label = cleanPlaceName.ifBlank { "Hedef Konum" }
        val encodedLabel = Uri.encode(label)

        // 1. Android Google Maps geo URI with exact pin and label
        // Format: geo:lat,lng?q=lat,lng(Label)
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")

        // 2. Google Maps Web URL with coordinates and label
        // Format: https://www.google.com/maps?q=lat,lng+(Label)
        val webUri = Uri.parse("https://www.google.com/maps?q=$latitude,$longitude+($encodedLabel)")
        val webSearchUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(if (cleanPlaceName.isNotBlank()) "$cleanPlaceName, $latitude,$longitude" else "$latitude,$longitude")}")

        // Attempt 1: Target official Google Maps app with geo URI (displays pin & place name)
        try {
            val googleMapsAppIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(googleMapsAppIntent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Could not open with Google Maps package, trying generic geo intent", e)
        }

        // Attempt 2: Generic geo URI (opens any installed map application, e.g. Maps Go, Waze, etc.)
        try {
            val genericGeoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericGeoIntent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Could not open with generic geo intent, trying web browser fallback", e)
        }

        // Attempt 3: Open in browser using Google Maps web URL with pin and label
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Web browser with direct label failed, trying search URL", e)
        }

        // Attempt 4: Fallback to Google Maps Search API web URL
        try {
            val searchIntent = Intent(Intent.ACTION_VIEW, webSearchUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(searchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "All map launching attempts failed", e)
        }
    }

    /**
     * Opens Google Maps turn-by-turn navigation / walking directions
     */
    fun openRouteInGoogleMaps(
        context: Context,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ) {
        val routeUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=walking")
        val appIntent = Intent(Intent.ACTION_VIEW, routeUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(appIntent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, routeUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }
}
