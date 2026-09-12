package com.example.gemini

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.location.GeocodingService
import com.example.location.PlaceNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiMapsService(private val context: Context? = null) {

    private val geocodingService = GeocodingService(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Searches places using:
     * 1. Gemini 2.5 Flash with Google Maps Grounding (if key available)
     * 2. Comprehensive GeocodingService (Nominatim, Geocoder, Curated Landmarks)
     *
     * CRITICAL: If no place is found anywhere, returns Result.failure(PlaceNotFoundException)
     * so that the user is notified and the current user location is NOT wrongly recorded!
     */
    suspend fun searchPlaceWithMapsGrounding(
        query: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): Result<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Yer araması boş olamaz"))
        }

        val apiKey = BuildConfig.GEMINI_API_KEY

        // Try Gemini with Maps Grounding if API key is present
        if (!apiKey.isNullOrEmpty() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val promptText = """
                    Sen bir Google Haritalar (Google Maps) ve konum asistanısın.
                    Kullanıcı şu yer hakkında arama yaptı: "$cleanQuery"
                    ${if (userLatitude != null && userLongitude != null) "Kullanıcının mevcut yaklaşık konumu: enlem $userLatitude, boylam $userLongitude" else ""}
                    
                    Google Maps verilerini kullanarak yerin en güncel ve gerçek coğrafi konumunu, adresini, kategorisini tespit et.
                    Ayrıca kullanıcının bu konuma vardığında hatırlamak isteyebileceği 2-3 pratik görev önerisi üret.
                    Eğer yer bulunamazsa veya belirsizse lütfen hiçbir yer uydurma.
                    
                    Cevabını SADECE geçerli bir JSON nesnesi olarak ver:
                    {
                      "placeName": "Yerin tam resmi adı",
                      "category": "Kategori adı (İşyeri, Park, Tiyatro / Kültür, Market, Kafe / Restoran veya Diğer)",
                      "address": "Açık ve net adres veya semt/şehir bilgisi",
                      "latitude": 41.1082,
                      "longitude": 29.0543,
                      "summary": "Yer hakkında bilgi",
                      "suggestedTasks": ["Görev önerisi 1", "Görev önerisi 2"],
                      "mapsUrl": "https://maps.google.com/?q=..."
                    }
                """.trimIndent()

                val rootJson = JSONObject().apply {
                    val parts = JSONArray().put(JSONObject().put("text", promptText))
                    put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    val tools = JSONArray().put(JSONObject().put("googleMaps", JSONObject()))
                    put("tools", tools)
                }

                val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val parsed = parseGeminiResponse(responseBody, cleanQuery)
                    if (parsed != null) {
                        return@withContext Result.success(parsed)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini search error, falling back to GeocodingService", e)
            }
        }

        // Fallback to robust GeocodingService (Nominatim, native geocoder, landmarks)
        val geoResult = geocodingService.searchPlace(cleanQuery, userLatitude, userLongitude)
        geoResult.mapCatching { list ->
            list.firstOrNull() ?: throw PlaceNotFoundException(cleanQuery)
        }
    }

    /**
     * Resolves multiple matching candidates for a query
     */
    suspend fun searchMultiplePlaces(
        query: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): Result<List<PlaceSearchResult>> = withContext(Dispatchers.IO) {
        geocodingService.searchPlace(query, userLatitude, userLongitude)
    }

    private fun parseGeminiResponse(
        responseBody: String,
        originalQuery: String
    ): PlaceSearchResult? {
        try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    val jsonString = extractJsonFromText(text)
                    if (jsonString.isNotEmpty()) {
                        val obj = JSONObject(jsonString)
                        val lat = obj.optDouble("latitude", Double.NaN)
                        val lng = obj.optDouble("longitude", Double.NaN)
                        if (!lat.isNaN() && !lng.isNaN()) {
                            val placeName = obj.optString("placeName", originalQuery)
                            val category = obj.optString("category", inferCategory(placeName))
                            val address = obj.optString("address", "Harita konumu")
                            val summary = obj.optString("summary", "Google Maps üzerinden güncel konum doğrulandı.")
                            val mapsUrl = obj.optString("mapsUrl", "https://maps.google.com/?q=$lat,$lng")

                            val tasksList = mutableListOf<String>()
                            val tasksJson = obj.optJSONArray("suggestedTasks")
                            if (tasksJson != null) {
                                for (i in 0 until tasksJson.length()) {
                                    val t = tasksJson.optString(i)
                                    if (t.isNotBlank()) tasksList.add(t)
                                }
                            }

                            return PlaceSearchResult(
                                placeName = placeName,
                                category = category,
                                address = address,
                                latitude = lat,
                                longitude = lng,
                                summary = summary,
                                suggestedTasks = tasksList,
                                mapsUrl = mapsUrl,
                                isGrounded = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse structured JSON from Gemini response", e)
        }
        return null
    }

    private fun extractJsonFromText(rawText: String): String {
        val trimmed = rawText.trim()
        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        return if (startIndex in 0 until endIndex) {
            trimmed.substring(startIndex, endIndex + 1)
        } else {
            ""
        }
    }

    private fun inferCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("iş") || lower.contains("ofis") || lower.contains("plaza") || lower.contains("şirket") -> "İşyeri"
            lower.contains("park") || lower.contains("koru") || lower.contains("bahçe") || lower.contains("orman") -> "Park"
            lower.contains("tiyatro") || lower.contains("sinema") || lower.contains("sahne") || lower.contains("kültür") || lower.contains("müze") || lower.contains("opera") -> "Tiyatro / Kültür"
            lower.contains("market") || lower.contains("avm") || lower.contains("bakkal") || lower.contains("çarşı") -> "Market"
            lower.contains("kafe") || lower.contains("cafe") || lower.contains("kahve") || lower.contains("restoran") || lower.contains("lokanta") -> "Kafe / Restoran"
            else -> "Diğer"
        }
    }

    companion object {
        private const val TAG = "GeminiMapsService"
    }
}

