package com.example.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.gemini.PlaceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Multi-layer Geocoding and Place Resolution Service.
 * Resolves real-world geographic coordinates (Latitude, Longitude, Address, Category)
 * using:
 * 1. OpenStreetMap Nominatim Global Engine (accurate, offline-friendly network call, no API key needed)
 * 2. Android native Geocoder API
 * 3. Gemini 2.5 Flash Maps Grounding (when API key is present)
 * 4. Curated Turkish & World Landmarks Registry
 *
 * CRITICAL RULE: If a place is NOT found, it NEVER substitutes the user's current location!
 * It strictly returns an empty result / failure so the user is alerted with an error message.
 */
class GeocodingService(private val context: Context? = null) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Searches for places matching [query].
     * Returns Result.success(List<PlaceSearchResult>) if found,
     * or Result.failure with PlaceNotFoundException if no matching place could be resolved.
     */
    suspend fun searchPlace(
        query: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): Result<List<PlaceSearchResult>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Sorgu boş olamaz"))
        }

        val results = mutableListOf<PlaceSearchResult>()

        // 1. Try Curated Landmarks Registry first for instant, accurate matching
        findInCuratedLandmarks(cleanQuery)?.let {
            results.add(it)
        }

        // 2. Try OpenStreetMap Nominatim Geocoding (real-world streets, districts, venues, shops)
        try {
            val nominatimResults = searchNominatim(cleanQuery)
            for (p in nominatimResults) {
                if (results.none { areCoordinatesClose(it.latitude, it.longitude, p.latitude, p.longitude) }) {
                    results.add(p)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim search error for '$cleanQuery'", e)
        }

        // 3. Try Android Native Geocoder API if context is available
        if (context != null && results.size < 3) {
            try {
                val nativeResults = searchNativeGeocoder(cleanQuery, context)
                for (p in nativeResults) {
                    if (results.none { areCoordinatesClose(it.latitude, it.longitude, p.latitude, p.longitude) }) {
                        results.add(p)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native Geocoder search error for '$cleanQuery'", e)
            }
        }

        // 4. Try Gemini 2.5 Flash Maps Grounding if API key is provided
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!apiKey.isNullOrEmpty() && apiKey != "MY_GEMINI_API_KEY" && results.isEmpty()) {
            try {
                val geminiPlace = searchWithGemini(cleanQuery, apiKey, userLatitude, userLongitude)
                if (geminiPlace != null) {
                    results.add(geminiPlace)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Maps search error for '$cleanQuery'", e)
            }
        }

        // 5. CRITICAL CHECK: If nothing matched, RETURN FAILURE!
        // NEVER substitute user location as the place location!
        if (results.isEmpty()) {
            Log.i(TAG, "Place NOT found for query: '$cleanQuery'")
            return@withContext Result.failure(PlaceNotFoundException(cleanQuery))
        }

        Result.success(results.distinctBy { "${it.placeName}-${it.latitude}-${it.longitude}" })
    }

    /**
     * Resolves the coordinate of a place using Nominatim OpenStreetMap API.
     */
    private fun searchNominatim(query: String): List<PlaceSearchResult> {
        val firstAttempt = queryUrl(query)
        if (firstAttempt.isNotEmpty()) return firstAttempt

        // If not found on first try, try appending Turkey / Türkiye context
        if (!query.contains("Türkiye", ignoreCase = true) && !query.contains("Turkey", ignoreCase = true)) {
            val turkeyAttempt = queryUrl("$query, Türkiye")
            if (turkeyAttempt.isNotEmpty()) return turkeyAttempt
        }
        return emptyList()
    }

    private fun queryUrl(searchQuery: String): List<PlaceSearchResult> {
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5&accept-language=tr,en"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeoTaskTracker-AndroidApp/3.0 (geotask@example.com)")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim network error for '$searchQuery'", e)
            return emptyList()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Nominatim returned HTTP ${response.code}")
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val jsonArray = try {
            JSONArray(body)
        } catch (e: Exception) {
            return emptyList()
        }
        val list = mutableListOf<PlaceSearchResult>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val displayName = item.optString("display_name", searchQuery)
            val name = item.optString("name").ifBlank {
                displayName.split(",").firstOrNull()?.trim() ?: searchQuery
            }
            val type = item.optString("type", "")
            val clazz = item.optString("class", "")

            val category = categorize(name, type, clazz)
            val summary = generateSummary(name, category, displayName)

            list.add(
                PlaceSearchResult(
                    placeName = name,
                    category = category,
                    address = formatAddressFromDisplayName(displayName),
                    latitude = lat,
                    longitude = lon,
                    summary = summary,
                    suggestedTasks = generateSuggestedTasks(name, category),
                    mapsUrl = "https://maps.google.com/?q=$lat,$lon",
                    isGrounded = true
                )
            )
        }
        return list
    }

    /**
     * Uses Android's built-in Geocoder
     */
    @Suppress("DEPRECATION")
    private fun searchNativeGeocoder(query: String, ctx: Context): List<PlaceSearchResult> {
        if (!Geocoder.isPresent()) return emptyList()

        val geocoder = Geocoder(ctx, Locale.getDefault())
        val addresses: List<Address> = try {
            geocoder.getFromLocationName(query, 5) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return addresses.mapNotNull { addr ->
            if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
            val lat = addr.latitude
            val lng = addr.longitude
            val featureName = addr.featureName?.takeIf { it.isNotBlank() }
                ?: addr.thoroughfare
                ?: query
            val fullAddress = (0..addr.maxAddressLineIndex)
                .mapNotNull { addr.getAddressLine(it) }
                .joinToString(", ")
                .ifBlank { "${addr.subAdminArea ?: ""}, ${addr.adminArea ?: ""}" }

            val category = inferCategoryFromText("$featureName $fullAddress")

            PlaceSearchResult(
                placeName = featureName,
                category = category,
                address = fullAddress,
                latitude = lat,
                longitude = lng,
                summary = "Doğrulanmış konum: $fullAddress",
                suggestedTasks = generateSuggestedTasks(featureName, category),
                mapsUrl = "https://maps.google.com/?q=$lat,$lng",
                isGrounded = true
            )
        }
    }

    /**
     * Reverse geocode a coordinate into a human-readable address
     */
    @Suppress("DEPRECATION")
    fun reverseGeocode(lat: Double, lng: Double, ctx: Context?): String {
        if (ctx != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(ctx, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val line = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
                    if (line.isNotBlank()) return line
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reverse geocoding error", e)
            }
        }
        return String.format(Locale.US, "%.5f, %.5f", lat, lng)
    }

    /**
     * Optional Gemini grounding call
     */
    private fun searchWithGemini(
        query: String,
        apiKey: String,
        userLat: Double?,
        userLng: Double?
    ): PlaceSearchResult? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val promptText = """
            Kullanıcı şu yeri aradı: "$query"
            Bu yerin gerçek dünyadaki koordinatlarını, açık adresini ve kategorisini belirle.
            Eğer yer gerçekte yoksa veya bulunamıyorsa hiçbir şey üretme.
            Cevap formatı SADECE geçerli JSON olmalıdır:
            {
              "placeName": "Resmi Adı",
              "category": "Kategori",
              "address": "Açık adres",
              "latitude": 41.0,
              "longitude": 29.0,
              "summary": "Özet bilgi",
              "suggestedTasks": ["Görev 1", "Görev 2"]
            }
        """.trimIndent()

        val rootJson = JSONObject().apply {
            val parts = JSONArray().put(JSONObject().put("text", promptText))
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val text = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text") ?: return null

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val json = JSONObject(text.substring(start, end + 1))
        val lat = json.optDouble("latitude", Double.NaN)
        val lng = json.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null

        return PlaceSearchResult(
            placeName = json.optString("placeName", query),
            category = json.optString("category", "Diğer"),
            address = json.optString("address", "Harita konumu"),
            latitude = lat,
            longitude = lng,
            summary = json.optString("summary", ""),
            suggestedTasks = listOf(json.optString("placeName", query) + " konumunda görevi tamamla"),
            mapsUrl = "https://maps.google.com/?q=$lat,$lng",
            isGrounded = true
        )
    }

    private fun areCoordinatesClose(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
        return Math.abs(lat1 - lat2) < 0.0005 && Math.abs(lon1 - lon2) < 0.0005
    }

    private fun categorize(name: String, type: String, clazz: String): String {
        val lower = "$name $type $clazz".lowercase()
        return when {
            lower.contains("office") || lower.contains("iş") || lower.contains("ofis") || lower.contains("plaza") || lower.contains("holding") || lower.contains("company") -> "İşyeri"
            lower.contains("park") || lower.contains("garden") || lower.contains("koru") || lower.contains("bahçe") || lower.contains("forest") || lower.contains("orman") -> "Park"
            lower.contains("theatre") || lower.contains("theater") || lower.contains("tiyatro") || lower.contains("opera") || lower.contains("museum") || lower.contains("müze") || lower.contains("cinema") || lower.contains("sinema") || lower.contains("kültür") -> "Tiyatro / Kültür"
            lower.contains("market") || lower.contains("supermarket") || lower.contains("mall") || lower.contains("avm") || lower.contains("bazaar") || lower.contains("çarşı") || lower.contains("shop") -> "Market"
            lower.contains("cafe") || lower.contains("kafe") || lower.contains("restaurant") || lower.contains("restoran") || lower.contains("coffee") || lower.contains("kahve") || lower.contains("bar") || lower.contains("pub") -> "Kafe / Restoran"
            else -> "Diğer"
        }
    }

    private fun inferCategoryFromText(text: String): String {
        return categorize(text, "", "")
    }

    private fun formatAddressFromDisplayName(displayName: String): String {
        val parts = displayName.split(",")
        return if (parts.size > 4) {
            parts.take(4).joinToString(",").trim()
        } else {
            displayName.trim()
        }
    }

    private fun generateSummary(name: String, category: String, address: String): String {
        return "$name: $category kategorisinde, $address adresinde harita konumu doğrulandı."
    }

    private fun generateSuggestedTasks(name: String, category: String): List<String> {
        return when (category) {
            "İşyeri" -> listOf("$name ofisinde toplantı ve evrak teslimini yap", "Görüşme notlarını gözden geçir")
            "Park" -> listOf("$name içinde yürüyüş ve mola ver", "Fotoğraf çek ve dinlen")
            "Tiyatro / Kültür" -> listOf("$name gişesinden biletleri teslim al", "Etkinlik saatinden önce salonda ol")
            "Market" -> listOf("$name şubesinden alışveriş listesini tamamla", "Eksikleri temin et")
            "Kafe / Restoran" -> listOf("$name mekanında kahve molası ver", "Görüşme yap")
            else -> listOf("$name konumundaki görevi tamamla")
        }
    }

    /**
     * Curated catalog of famous places in Turkey and major capitals for instantaneous matching
     */
    private fun findInCuratedLandmarks(query: String): PlaceSearchResult? {
        val qNorm = normalizeForMatch(query)
        if (qNorm.isBlank()) return null
        for (item in LANDMARKS) {
            val nameNorm = normalizeForMatch(item.officialName)
            if (qNorm == nameNorm || qNorm.contains(nameNorm) || nameNorm.contains(qNorm)) {
                return item.toResult()
            }
            for (alias in item.aliases) {
                val aliasNorm = normalizeForMatch(alias)
                if (qNorm == aliasNorm || qNorm.contains(aliasNorm) || aliasNorm.contains(qNorm)) {
                    return item.toResult()
                }
            }
        }
        return null
    }

    private fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace('ı', 'i')
            .replace("i̇", "i")
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    private data class LandmarkEntry(
        val aliases: List<String>,
        val officialName: String,
        val category: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val summary: String,
        val tasks: List<String>
    ) {
        fun toResult() = PlaceSearchResult(
            placeName = officialName,
            category = category,
            address = address,
            latitude = lat,
            longitude = lng,
            summary = summary,
            suggestedTasks = tasks,
            mapsUrl = "https://maps.google.com/?q=$lat,$lng",
            isGrounded = true
        )
    }

    companion object {
        private const val TAG = "GeocodingService"

        private val LANDMARKS = listOf(
            LandmarkEntry(
                aliases = listOf("anıtkabir", "anitkabir", "atatürk anıtkabir", "ataturk anitkabir"),
                officialName = "Anıtkabir",
                category = "Tiyatro / Kültür",
                address = "Anıt Cad. Tandoğan, Çankaya / Ankara",
                lat = 39.92505,
                lng = 32.83695,
                summary = "Mustafa Kemal Atatürk'ün anıt mezarı ve müzesi. Ziyaret saatleri 09:00 - 17:00.",
                tasks = listOf("Müze bölümünü ziyaret et", "Tören alanını ve nöbet değişimini incele")
            ),
            LandmarkEntry(
                aliases = listOf("süreyya", "sureyya", "süreyya operası", "sureyya operasi", "süreyya tiyatrosu"),
                officialName = "Kadıköy Süreyya Tiyatrosu",
                category = "Tiyatro / Kültür",
                address = "Bahariye Cad. No:29, Kadıköy / İstanbul",
                lat = 40.9897,
                lng = 29.0289,
                summary = "Kadıköy'ün tarihi opera ve tiyatro sahnesi. Temsil biletleri gişeden alınabilir.",
                tasks = listOf("Gişeden cuma günkü rezerve biletleri teslim al", "Etkinlik başlamadan 20 dk önce salonda ol")
            ),
            LandmarkEntry(
                aliases = listOf("kadıköy iskele", "kadikoy iskele", "kadıköy vapur iskelesi", "kadikoy rihtim"),
                officialName = "Kadıköy Vapur İskelesi",
                category = "Diğer",
                address = "Rıhtım Cad., Kadıköy / İstanbul",
                lat = 40.9912,
                lng = 29.0227,
                summary = "Şehir Hatları Beşiktaş ve Karaköy vapurlarının kalkış noktası.",
                tasks = listOf("Vapur kalkış saatini kontrol et", "İstanbulkart bakiyesini doldur")
            ),
            LandmarkEntry(
                aliases = listOf("kadıköy boğa", "kadikoy boga", "boga heykeli", "kadıköy altıyol boğa"),
                officialName = "Kadıköy Boğa Heykeli",
                category = "Diğer",
                address = "Altıyol Meydanı, Kadıköy / İstanbul",
                lat = 40.9904,
                lng = 29.0298,
                summary = "Kadıköy'ün simgesi tarihi Altıyol Boğa Heykeli buluşma noktası.",
                tasks = listOf("Buluşma noktasına zamanında var", "Çevredeki kitapçıları gez")
            ),
            LandmarkEntry(
                aliases = listOf("moda sahili", "kadıköy moda", "moda parkı", "moda iskelesi"),
                officialName = "Kadıköy Moda Sahili & Parkı",
                category = "Park",
                address = "Moda Cad., Kadıköy / İstanbul",
                lat = 40.9840,
                lng = 29.0250,
                summary = "Moda Burnu, çay bahçeleri ve tarihi Moda İskelesi.",
                tasks = listOf("Sahilde yürüyüş yap", "Moda İskelesi'nde mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("ayasofya", "hagia sophia", "ayasofya camii", "ayasofya-i kebir"),
                officialName = "Ayasofya-i Kebir Cami-i Şerifi",
                category = "Tiyatro / Kültür",
                address = "Sultanahmet Meydanı, Fatih / İstanbul",
                lat = 41.0086,
                lng = 28.9802,
                summary = "Tarihi yarımadanın dünyaca ünlü mimari ve kültürel anıtı.",
                tasks = listOf("Tarihi galerileri ve kubbeyi incele", "Meydanda fotoğraf çek")
            ),
            LandmarkEntry(
                aliases = listOf("sultanahmet", "sultanahmet camii", "mavi cami", "blue mosque"),
                officialName = "Sultanahmet Camii",
                category = "Tiyatro / Kültür",
                address = "Sultan Ahmet, Atmeydanı Cd. No:7, Fatih / İstanbul",
                lat = 41.0054,
                lng = 28.9768,
                summary = "6 minareli tarihi Mavi Cami. Çevresinde hipodrom ve dikilitaşlar bulunur.",
                tasks = listOf("Tarihi atmosferi incele", "Turistik broşür al")
            ),
            LandmarkEntry(
                aliases = listOf("galata", "galata kulesi", "galata tower"),
                officialName = "Galata Kulesi",
                category = "Tiyatro / Kültür",
                address = "Bereketzade, Beyoğlu / İstanbul",
                lat = 41.0256,
                lng = 28.9741,
                summary = "İstanbul Boğazı ve Haliç'i gören tarihi gözetleme kulesi ve seyir terası.",
                tasks = listOf("Seyir terasından panaromik şehir fotoğrafı çek", "Karaköy yönüne yürüyüş yap")
            ),
            LandmarkEntry(
                aliases = listOf("taksim", "taksim meydanı", "taksim meydani", "istiklal caddesi", "taksim gezi"),
                officialName = "Taksim Meydanı & İstiklal Caddesi",
                category = "Diğer",
                address = "Gümüşsuyu, Beyoğlu / İstanbul",
                lat = 41.0370,
                lng = 28.9850,
                summary = "Cumhuriyet Anıtı ve İstiklal Caddesi başlangıç noktası.",
                tasks = listOf("Cumhuriyet Anıtı önünde buluş", "Tarihi tramvay güzergahını takip et")
            ),
            LandmarkEntry(
                aliases = listOf("beşiktaş meydanı", "besiktas meydani", "beşiktaş çarşı", "besiktas carsi", "beşiktaş iskele"),
                officialName = "Beşiktaş Meydanı & Çarşı",
                category = "Market",
                address = "Sinanpaşa, Beşiktaş / İstanbul",
                lat = 41.0425,
                lng = 29.0068,
                summary = "Beşiktaş İskelesi, Barbaros Hayrettin Paşa Türbesi ve hareketli çarşı bölgesi.",
                tasks = listOf("Çarşı içinde alışveriş yap", "İskele kafesinde mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("üsküdar meydanı", "uskudar meydani", "üsküdar iskele", "kız kulesi", "kiz kulesi"),
                officialName = "Üsküdar Meydanı & Kız Kulesi Sahili",
                category = "Tiyatro / Kültür",
                address = "Mimar Sinan, Üsküdar / İstanbul",
                lat = 41.0264,
                lng = 29.0150,
                summary = "Tarihi camiler, Marmaray istasyonu ve Kız Kulesi manzaralı sahil şeridi.",
                tasks = listOf("Kız Kulesi manzaralı bankta otur", "Marmaray aktarmasını kullan")
            ),
            LandmarkEntry(
                aliases = listOf("eminönü meydanı", "eminonu meydani", "mısır çarşısı", "misir carsisi", "yeni cami"),
                officialName = "Eminönü Meydanı & Mısır Çarşısı",
                category = "Market",
                address = "Rüstem Paşa, Fatih / İstanbul",
                lat = 41.0175,
                lng = 28.9705,
                summary = "Tarihi Mısır Çarşısı, balık ekmek tekneleri ve Galata Köprüsü ayağı.",
                tasks = listOf("Mısır Çarşısı'ndan baharat al", "Galata Köprüsü üzerinden yürü")
            ),
            LandmarkEntry(
                aliases = listOf("kapalıçarşı", "kapalicarsi", "grand bazaar"),
                officialName = "Tarihi Kapalıçarşı",
                category = "Market",
                address = "Beyazıt, Fatih / İstanbul",
                lat = 41.0107,
                lng = 28.9680,
                summary = "Dünyanın en eski ve en büyük kapalı çarşılarından biri.",
                tasks = listOf("Kapalıçarşı esnafından hediyelik eşya al", "Geleneksel kahvehanede mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("topkapı sarayı", "topkapi sarayi", "topkapi palace"),
                officialName = "Topkapı Sarayı Müzesi",
                category = "Tiyatro / Kültür",
                address = "Cankurtaran, Fatih / İstanbul",
                lat = 41.0115,
                lng = 28.9833,
                summary = "Osmanlı padişahlarının 400 yıl ikamet ettiği tarihi saray kompleksi.",
                tasks = listOf("Harem ve Kutsal Emanetler dairesini gez", "Gülhane Parkı'na geç")
            ),
            LandmarkEntry(
                aliases = listOf("dolmabahçe sarayı", "dolmabahce sarayi", "dolmabahce palace"),
                officialName = "Dolmabahçe Sarayı",
                category = "Tiyatro / Kültür",
                address = "Vişnezade, Dolmabahçe Cd., Beşiktaş / İstanbul",
                lat = 41.0392,
                lng = 29.0003,
                summary = "Boğaz kıyısında neobarok ve ampir mimarili tarihi saray ve saat kulesi.",
                tasks = listOf("Saray bahçesinde gezinti yap", "Saat kulesi önünde fotoğraf çek")
            ),
            LandmarkEntry(
                aliases = listOf("ortaköy", "ortakoy", "ortaköy camii", "büyük mecidiyeköy camii"),
                officialName = "Ortaköy Meydanı & Camii",
                category = "Kafe / Restoran",
                address = "Ortaköy Meydanı, Beşiktaş / İstanbul",
                lat = 41.0474,
                lng = 29.0270,
                summary = "Boğaziçi Köprüsü altında tarihi Ortaköy Camii, kumpir ve kafeler meydanı.",
                tasks = listOf("Kumpir veya waffle molası ver", "Boğaz manzaralı kafede dinlen")
            ),
            LandmarkEntry(
                aliases = listOf("emirgan", "emirgan parkı", "emirgan korusu", "sarı köşk"),
                officialName = "Emirgan Parkı & Korusu",
                category = "Park",
                address = "Reşitpaşa, Sarıyer / İstanbul",
                lat = 41.1084,
                lng = 29.0543,
                summary = "Lale bahçeleri, sarı ve pembe köşkleri barındıran geniş yeşil koru.",
                tasks = listOf("Gölet etrafında 30 dk tempolu yürüyüş yap", "Sarı Köşk'te mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("maslak plaza", "maslak iş kuleleri", "maslak", "maslak itü"),
                officialName = "Maslak İş Kuleleri & Plaza",
                category = "İşyeri",
                address = "Büyükdere Cad. No:140, Maslak / Sarıyer",
                lat = 41.1118,
                lng = 29.0211,
                summary = "İstanbul'un ana iş, finans ve yönetim kuleleri merkezi.",
                tasks = listOf("Toplantı odasında çeyrek dönem sunumunu imzalat", "Ziyaretçi kaydı yaptır")
            ),
            LandmarkEntry(
                aliases = listOf("levent", "levent çarşı", "kanyon", "metrocity", "özdilek"),
                officialName = "Levent Kanyon & İş Merkezi",
                category = "İşyeri",
                address = "Büyükdere Cad. No:185, Levent / Şişli",
                lat = 41.0778,
                lng = 29.0118,
                summary = "Finans merkezleri, Kanyon AVM ve metro aktarma merkezi.",
                tasks = listOf("İş görüşmesini tamamla", "AVM'de alışveriş yap")
            ),
            LandmarkEntry(
                aliases = listOf("zorlu center", "zorlu", "zorlu avm", "zorlu psm"),
                officialName = "Zorlu Center & PSM",
                category = "Market",
                address = "Levazım, Koru Sokağı No:2, Beşiktaş / İstanbul",
                lat = 41.0667,
                lng = 29.0175,
                summary = "Lüks alışveriş merkezi ve performans sanatları merkezi (PSM).",
                tasks = listOf("Etkinlik biletini kontrol et", "Mağazaları gez")
            ),
            LandmarkEntry(
                aliases = listOf("cevahir", "cevahir avm", "mecidiyeköy cevahir"),
                officialName = "Mecidiyeköy Cevahir AVM",
                category = "Market",
                address = "Büyükdere Cad. No:22, Şişli / İstanbul",
                lat = 41.0628,
                lng = 28.9892,
                summary = "Avrupa'nın en büyük alışveriş ve eğlence merkezlerinden biri.",
                tasks = listOf("Elektronik ve giyim alışverişini yap", "Metro çıkışında buluş")
            ),
            LandmarkEntry(
                aliases = listOf("kızılay", "kizilay", "kızılay meydanı", "ankara kızılay", "güvenpark"),
                officialName = "Kızılay Meydanı & Güvenpark",
                category = "Diğer",
                address = "Kızılay, Çankaya / Ankara",
                lat = 39.9208,
                lng = 32.8541,
                summary = "Ankara'nın merkezi buluşma, ticaret ve ulaşım kavşağı.",
                tasks = listOf("Merkez noktada evrakları teslim al", "Metro aktarma istasyonunu kullan")
            ),
            LandmarkEntry(
                aliases = listOf("tunalı", "tunali", "tunalı hilmi", "kuğulu park", "kugulu park"),
                officialName = "Tunalı Hilmi Caddesi & Kuğulu Park",
                category = "Park",
                address = "Kavaklıdere, Çankaya / Ankara",
                lat = 39.9056,
                lng = 32.8606,
                summary = "Ankara'nın popüler alışveriş caddesi ve kuğularıyla ünlü şehir parkı.",
                tasks = listOf("Kuğulu Park'ta dinlen", "Tunalı Caddesi'nde kitapçıları gez")
            ),
            LandmarkEntry(
                aliases = listOf("atakule", "ankara atakule", "çankaya atakule"),
                officialName = "Atakule Seyir Kulesi & AVM",
                category = "Diğer",
                address = "Çankaya Cd. No:1, Çankaya / Ankara",
                lat = 39.8858,
                lng = 32.8558,
                summary = "Ankara'nın simge döner kulesi, Botanik Parkı komşusu.",
                tasks = listOf("Seyir terasından Ankara manzarasını izle", "Botanik Parkı'nda yürü")
            ),
            LandmarkEntry(
                aliases = listOf("ankara kalesi", "kale ankara", "altındağ kale"),
                officialName = "Tarihi Ankara Kalesi",
                category = "Tiyatro / Kültür",
                address = "Kale Mah., Altındağ / Ankara",
                lat = 39.9419,
                lng = 32.8644,
                summary = "Antik Roma ve Osmanlı izlerini taşıyan tarihi kale ve geleneksel dükkanlar.",
                tasks = listOf("Surlardan panoramik fotoğraf çek", "Geleneksel hanları gez")
            ),
            LandmarkEntry(
                aliases = listOf("konak saat kulesi", "izmir saat kulesi", "konak meydanı", "izmir konak"),
                officialName = "İzmir Konak Saat Kulesi & Meydanı",
                category = "Tiyatro / Kültür",
                address = "Konak Meydanı, Konak / İzmir",
                lat = 38.4189,
                lng = 27.1287,
                summary = "1901 yapımı İzmir'in simge saat kulesi ve Konak Meydanı.",
                tasks = listOf("Saat kulesi önünde buluş", "Kordon boyunda yürüyüş yap")
            ),
            LandmarkEntry(
                aliases = listOf("kordon", "izmir kordon", "alsancak kordon", "gündoğdu meydanı"),
                officialName = "İzmir Alsancak Kordon Sahili",
                category = "Park",
                address = "Atatürk Cad. Alsancak, Konak / İzmir",
                lat = 38.4344,
                lng = 27.1394,
                summary = "İzmir Körfezi boyunca uzanan çim alanlar ve sahil şeridi.",
                tasks = listOf("Sahilde bisiklet sür veya yürüyüş yap", "Gündoğdu Meydanı'nda mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("kemeraltı", "kemeralti", "kemeraltı çarşısı", "izmir kemeraltı"),
                officialName = "Tarihi Kemeraltı Çarşısı",
                category = "Market",
                address = "Konak, İzmir",
                lat = 38.4172,
                lng = 27.1333,
                summary = "Kızlarağası Hanı, tarihi camiler ve yüzlerce yıllık çarşı sokakları.",
                tasks = listOf("Kızlarağası Hanı'nda kumda kahve iç", "Çarşıdan yöresel ürünler al")
            ),
            LandmarkEntry(
                aliases = listOf("karşıyaka", "karsiyaka", "karşıyaka çarşı", "karşıyaka iskele"),
                officialName = "Karşıyaka İskelesi & Çarşı",
                category = "Diğer",
                address = "Cemal Gürsel Cd., Karşıyaka / İzmir",
                lat = 38.4560,
                lng = 27.1120,
                summary = "Karşıyaka vapur iskelesi ve araç trafiğine kapalı hareketli çarşı caddesi.",
                tasks = listOf("Çarşı boyunca yürüyüş yap", "Vapur ile Konak'a geç")
            ),
            LandmarkEntry(
                aliases = listOf("eskişehir gar", "eskisehir tren gari", "eskişehir yht garı"),
                officialName = "Eskişehir YHT Tren Garı",
                category = "Diğer",
                address = "Hoşnudiye Mah. İstasyon Cad., Tepebaşı / Eskişehir",
                lat = 39.7820,
                lng = 30.5140,
                summary = "Yüksek Hızlı Tren (YHT) ve ana demiryolu istasyonu.",
                tasks = listOf("Tren saatinden 15 dakika önce peronda ol", "Bileti hazırla")
            ),
            LandmarkEntry(
                aliases = listOf("odunpazarı", "odunpazari tarihi evleri", "eskişehir odunpazarı"),
                officialName = "Eskişehir Tarihi Odunpazarı Evleri",
                category = "Tiyatro / Kültür",
                address = "Akarbaşı, Kemal Zeytinoğlu Cd., Odunpazarı / Eskişehir",
                lat = 39.7607,
                lng = 30.5262,
                summary = "Geleneksel Osmanlı sivil mimarisi, lületaşı atölyeleri ve müzeler bölgesi.",
                tasks = listOf("Cam sanatları ve lületaşı atölyelerini gez", "Müze ziyaretlerini tamamla")
            ),
            LandmarkEntry(
                aliases = listOf("ulucami", "bursa ulu cami", "bursa ulucami"),
                officialName = "Bursa Ulu Camii",
                category = "Tiyatro / Kültür",
                address = "Nalbantoğlu, Atatürk Cd., Osmangazi / Bursa",
                lat = 40.1834,
                lng = 29.0614,
                summary = "Erken dönem Osmanlı mimarisinin 20 kubbeli şaheseri ve tarihi şadırvanı.",
                tasks = listOf("Tarihi hat levhalarını incele", "Kapalıçarşı bölgesini ziyaret et")
            ),
            LandmarkEntry(
                aliases = listOf("kaleiçi", "kaleici", "antalya kaleiçi", "hadrian kapısı", "üçkapılar"),
                officialName = "Antalya Tarihi Kaleiçi & Üçkapılar",
                category = "Tiyatro / Kültür",
                address = "Kaleiçi, Muratpaşa / Antalya",
                lat = 36.8841,
                lng = 30.7056,
                summary = "Roma dönemi Hadrian Kapısı (Üçkapılar), tarihi konaklar ve yat limanı.",
                tasks = listOf("Yat limanına inen sokaklarda yürü", "Tarihi surları incele")
            ),
            LandmarkEntry(
                aliases = listOf("mevlana", "mevlana müzesi", "konya mevlana"),
                officialName = "Konya Mevlana Müzesi & Türbesi",
                category = "Tiyatro / Kültür",
                address = "Aziziye Mah. Mevlana Cd. No:1, Karatay / Konya",
                lat = 37.8706,
                lng = 32.5050,
                summary = "Mevlana Celaleddin-i Rumi'nin türbesi ve müze kompleksi.",
                tasks = listOf("Müze sergi alanını gez", "Mevlevi kültürünü tanı")
            ),
            LandmarkEntry(
                aliases = listOf("trabzon meydan", "trabzon atatürk alanı", "meydan parkı trabzon"),
                officialName = "Trabzon Meydan Parkı & Atatürk Alanı",
                category = "Park",
                address = "İskenderpaşa, Ortahisar / Trabzon",
                lat = 41.0050,
                lng = 39.7269,
                summary = "Trabzon şehir merkezinin ana meydanı, çay bahçeleri ve tarihi binaları.",
                tasks = listOf("Meydan çay bahçesinde mola ver", "Uzun Sokak boyunca yürü")
            )
        )
    }
}

/**
 * Custom Exception thrown when a user-entered place cannot be geographically resolved.
 */
class PlaceNotFoundException(val query: String) : Exception("'$query' yeri için coğrafi konum bulunamadı.")
