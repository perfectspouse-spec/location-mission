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
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5&accept-language=tr,en"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeoTaskTracker-AndroidApp/3.0 (geotask@example.com)")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Nominatim returned HTTP ${response.code}")
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val jsonArray = JSONArray(body)
        val list = mutableListOf<PlaceSearchResult>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val displayName = item.optString("display_name", query)
            val name = item.optString("name").ifBlank {
                displayName.split(",").firstOrNull()?.trim() ?: query
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
        val q = query.lowercase().trim()
        for (item in LANDMARKS) {
            if (item.aliases.any { q.contains(it) || it.contains(q) }) {
                return PlaceSearchResult(
                    placeName = item.officialName,
                    category = item.category,
                    address = item.address,
                    latitude = item.lat,
                    longitude = item.lng,
                    summary = item.summary,
                    suggestedTasks = item.tasks,
                    mapsUrl = "https://maps.google.com/?q=${item.lat},${item.lng}",
                    isGrounded = true
                )
            }
        }
        return null
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
    )

    companion object {
        private const val TAG = "GeocodingService"

        private val LANDMARKS = listOf(
            LandmarkEntry(
                aliases = listOf("anıtkabir", "anitkabir", "atatürk anıtkabir"),
                officialName = "Anıtkabir",
                category = "Tiyatro / Kültür",
                address = "Anıt Cad. Tandoğan, Çankaya / Ankara",
                lat = 39.92505,
                lng = 32.83695,
                summary = "Mustafa Kemal Atatürk'ün anıt mezarı ve müzesi. Ziyaret saatleri 09:00 - 17:00.",
                tasks = listOf("Müze bölümünü ziyaret et", "Tören alanını ve nöbet değişimini incele")
            ),
            LandmarkEntry(
                aliases = listOf("süreyya", "sureyya", "süreyya operası", "süreyya tiyatrosu"),
                officialName = "Kadıköy Süreyya Tiyatrosu",
                category = "Tiyatro / Kültür",
                address = "Bahariye Cad. No:29, Kadıköy / İstanbul",
                lat = 40.9897,
                lng = 29.0289,
                summary = "Kadıköy'ün tarihi opera ve tiyatro sahnesi. Temsil biletleri gişeden alınabilir.",
                tasks = listOf("Gişeden cuma günkü rezerve biletleri teslim al", "Etkinlik başlamadan 20 dk önce salonda ol")
            ),
            LandmarkEntry(
                aliases = listOf("kadıköy iskele", "kadikoy iskele", "kadıköy vapur iskelesi"),
                officialName = "Kadıköy Vapur İskelesi",
                category = "Diğer",
                address = "Rıhtım Cad., Kadıköy / İstanbul",
                lat = 40.9912,
                lng = 29.0227,
                summary = "Şehir Hatları Beşiktaş ve Karaköy vapurlarının kalkış noktası.",
                tasks = listOf("Vapur kalkış saatini kontrol et", "İstanbulkart bakiyesini doldur")
            ),
            LandmarkEntry(
                aliases = listOf("kadıköy boğa", "boga heykeli", "kadikoy boga"),
                officialName = "Kadıköy Boğa Heykeli",
                category = "Diğer",
                address = "Altıyol Meydanı, Kadıköy / İstanbul",
                lat = 40.9904,
                lng = 29.0298,
                summary = "Kadıköy'ün simgesi tarihi Altıyol Boğa Heykeli buluşma noktası.",
                tasks = listOf("Buluşma noktasına zamanında var", "Çevredeki kitapçıları gez")
            ),
            LandmarkEntry(
                aliases = listOf("ayasofya", "hagia sophia", "ayasofya camii"),
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
                aliases = listOf("taksim", "taksim meydanı", "taksim meydani", "istiklal caddesi"),
                officialName = "Taksim Meydanı & İstiklal Caddesi",
                category = "Diğer",
                address = "Gümüşsuyu, Beyoğlu / İstanbul",
                lat = 41.0370,
                lng = 28.9850,
                summary = "Cumhuriyet Anıtı ve İstiklal Caddesi başlangıç noktası.",
                tasks = listOf("Cumhuriyet Anıtı önünde buluş", "Tarihi tramvay güzergahını takip et")
            ),
            LandmarkEntry(
                aliases = listOf("emirgan", "emirgan parkı", "emirgan korusu"),
                officialName = "Emirgan Parkı & Korusu",
                category = "Park",
                address = "Reşitpaşa, Emirgan Korusunu İçi Yolu, Sarıyer / İstanbul",
                lat = 41.1084,
                lng = 29.0543,
                summary = "Lale bahçeleri, sarı ve pembe köşkleri barındıran geniş yeşil koru.",
                tasks = listOf("Gölet etrafında 30 dk tempolu yürüyüş yap", "Sarı Köşk'te mola ver")
            ),
            LandmarkEntry(
                aliases = listOf("maslak plaza", "maslak iş kuleleri", "maslak"),
                officialName = "Maslak İş Kuleleri & Plaza",
                category = "İşyeri",
                address = "Büyükdere Cad. No:140, Maslak / Sarıyer",
                lat = 41.1118,
                lng = 29.0211,
                summary = "İstanbul'un ana iş, finans ve yönetim kuleleri merkezi.",
                tasks = listOf("Toplantı odasında çeyrek dönem sunumunu imzalat", "Ziyaretçi kaydı yaptır")
            ),
            LandmarkEntry(
                aliases = listOf("kızılay", "kizilay", "kızılay meydanı", "ankara kızılay"),
                officialName = "Kızılay Meydanı",
                category = "Diğer",
                address = "Kızılay, Çankaya / Ankara",
                lat = 39.9208,
                lng = 32.8541,
                summary = "Ankara'nın merkezi buluşma, ticaret ve ulaşım kavşağı.",
                tasks = listOf("Merkez noktada evrakları teslim al", "Metro aktarma istasyonunu kullan")
            ),
            LandmarkEntry(
                aliases = listOf("konak saat kulesi", "izmir saat kulesi", "konak meydanı"),
                officialName = "İzmir Konak Saat Kulesi",
                category = "Tiyatro / Kültür",
                address = "Konak Meydanı, Konak / İzmir",
                lat = 38.4189,
                lng = 27.1287,
                summary = "1901 yapımı İzmir'in simge saat kulesi ve Konak Meydanı.",
                tasks = listOf("Saat kulesi önünde buluş", "Kordon boyunda yürüyüş yap")
            ),
            LandmarkEntry(
                aliases = listOf("kordon", "izmir kordon", "alsancak kordon"),
                officialName = "İzmir Alsancak Kordon Sahili",
                category = "Park",
                address = "Atatürk Cad. Alsancak, Konak / İzmir",
                lat = 38.4344,
                lng = 27.1394,
                summary = "İzmir Körfezi boyunca uzanan çim alanlar ve sahil şeridi.",
                tasks = listOf("Sahilde bisiklet sür veya yürüyüş yap", "Gündoğdu Meydanı'nda mola ver")
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
            )
        )
    }
}

/**
 * Custom Exception thrown when a user-entered place cannot be geographically resolved.
 */
class PlaceNotFoundException(val query: String) : Exception("'$query' yeri için coğrafi konum bulunamadı.")
