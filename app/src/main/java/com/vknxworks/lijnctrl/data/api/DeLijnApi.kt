package com.vknxworks.lijnctrl.data.api

import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A De Lijn API error, with a message that can be shown as is. */
class ApiException(val code: Int, message: String) : IOException(message)

/** One stop, as used everywhere in the app: entity number + stop number. */
data class StopKey(val entity: String, val number: String) {
    override fun toString() = "${entity}_$number"
}

/** One line: entity number + the API's internal line number (not the number on the bus). */
data class LineKey(val entity: String, val line: String) {
    override fun toString() = "${entity}_$line"
}

/**
 * The De Lijn Open Data API (https://data.delijn.be). One subscription key covers the Core API (stops, lines,
 * real-time departures, disruptions) and the Search API. Only this host is ever contacted, over HTTPS.
 *
 * Quirks of the live API, handled here:
 * - the Search API swaps latitude and longitude in stop coordinates;
 * - identifiers come as numbers or strings (the lenient JSON reader turns both into String);
 * - error bodies are {"code","boodschap"} from the backend or {"statusCode","message"} from the gateway.
 */
class DeLijnApi(private val http: OkHttpClient, private val apiKey: () -> String?) {

    private suspend inline fun <reified T> get(base: String, path: String, query: Map<String, Any?> = emptyMap(), key: String? = null): T {
        val body = getRaw(base, path, query, key)
        return JSON.decodeFromString(body)
    }

    private suspend fun getRaw(base: String, path: String, query: Map<String, Any?>, key: String?): String = withContext(Dispatchers.IO) {
        val k = key ?: apiKey() ?: throw ApiException(0, str(R.string.no_api_key))
        val url: HttpUrl = (base + path).toHttpUrl().newBuilder().apply {
            query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value.toString()) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", k)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(response.code, text))
            text
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val obj = JSON.parseToJsonElement(body).jsonObject
            (obj["boodschap"] ?: obj["message"])?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> str(R.string.api_key_refused)
            403 -> str(R.string.api_key_not_allowed)
            404 -> str(R.string.not_found_at_de_lijn)
            429 -> str(R.string.too_many_requests)
            in 500..599 -> str(R.string.de_lijn_server_error, code)
            else -> detail?.takeIf { it.isNotBlank() } ?: str(R.string.de_lijn_error_x, code)
        }
    }

    // ------------------------------------------------------------- search

    /** Stops whose name matches [query]. Coordinates come back swapped from this API and are put right here. */
    suspend fun searchStops(query: String, max: Int = 30, key: String? = null): StopSearchResponse {
        val r: StopSearchResponse = get(SEARCH, "/zoek/haltes/${seg(query.trim())}", mapOf("maxAantalHits" to max), key)
        return r.copy(stops = r.stops.map { s -> s.copy(coordinate = s.coordinate?.let(::fixSwapped)) })
    }

    // ------------------------------------------------------------- core

    /** Checks the key against the core API (the search API is checked with [searchStops]). */
    suspend fun entities(key: String? = null): EntityList = get(CORE, "/entiteiten", key = key)

    suspend fun stop(stop: StopKey): ApiStop = get(CORE, "/haltes/${seg(stop.entity)}/${seg(stop.number)}")

    /** Stops within [radius] metres of a point, nearest first (the API caps it at 50 stops). */
    suspend fun stopsNearby(lat: Double, lon: Double, max: Int = 30, radius: Int = 750): NearbyResponse =
        get(CORE, "/haltes/indebuurt/$lat,$lon", mapOf("maxAantalHaltes" to max, "radius" to radius))

    /** The next departures at one stop, with live predictions where De Lijn has them. */
    suspend fun realtime(stop: StopKey, max: Int = 20): ApiStopSchedule =
        get(CORE, "/haltes/${seg(stop.entity)}/${seg(stop.number)}/real-time", mapOf("maxAantalDoorkomsten" to max))

    /** The next departures at several stops in one call. */
    suspend fun realtime(stops: List<StopKey>, max: Int = 4): List<ApiStopSchedule> {
        if (stops.isEmpty()) return emptyList()
        val keys = stops.joinToString("_") { "${seg(it.entity)}_${seg(it.number)}" }
        return get<StopScheduleList>(CORE, "/haltes/lijst/$keys/real-time", mapOf("maxAantalDoorkomsten" to max)).schedules
    }

    /** Line directions that call at a stop. */
    suspend fun stopLines(stop: StopKey): List<ApiLineDirection> =
        get<LineDirectionList>(CORE, "/haltes/${seg(stop.entity)}/${seg(stop.number)}/lijnrichtingen").directions

    /** Disruptions at a stop today. */
    suspend fun stopDisruptions(stop: StopKey): List<ApiDisruption> =
        get<DisruptionList>(CORE, "/haltes/${seg(stop.entity)}/${seg(stop.number)}/storingen").all

    /** Diversions (planned works) at a stop today. */
    suspend fun stopDiversions(stop: StopKey): List<ApiDisruption> =
        get<DisruptionList>(CORE, "/haltes/${seg(stop.entity)}/${seg(stop.number)}/omleidingen").all

    suspend fun lines(lines: List<LineKey>): List<ApiLine> {
        if (lines.isEmpty()) return emptyList()
        return get<LineList>(CORE, "/lijnen/lijst/${linesPath(lines)}").lines
    }

    suspend fun lineColors(lines: List<LineKey>): List<ApiLineColors> {
        if (lines.isEmpty()) return emptyList()
        return get<LineColorList>(CORE, "/lijnen/lijst/${linesPath(lines)}/lijnkleuren").lines
    }

    suspend fun colors(): List<ApiColor> = get<ColorList>(CORE, "/kleuren").colors

    private fun linesPath(lines: List<LineKey>) = lines.joinToString("_") { "${seg(it.entity)}_${seg(it.line)}" }

    companion object {
        const val CORE = "https://api.delijn.be/DLKernOpenData/api/v1"
        const val SEARCH = "https://api.delijn.be/DLZoekOpenData/v1"
        const val HOST = "api.delijn.be"

        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        /** In Flanders the latitude (~51) is always larger than the longitude (~2-6), so a swap is easy to spot. */
        fun fixSwapped(c: GeoCoordinate): GeoCoordinate =
            if (c.latitude < c.longitude) GeoCoordinate(latitude = c.longitude, longitude = c.latitude) else c

        /** The entity number of a nearby stop, read from its links (…/haltes/{entity}/{stop}/…). */
        fun entityFromLinks(stop: ApiNearbyStop): String? = stop.links.firstNotNullOfOrNull { link ->
            link.url?.let { Regex("/haltes/(\\d+)/(\\d+)").find(it) }?.groupValues?.get(1)
        }

        private fun seg(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    }
}
