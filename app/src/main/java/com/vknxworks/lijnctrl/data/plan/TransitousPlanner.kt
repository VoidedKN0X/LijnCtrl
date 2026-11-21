package com.vknxworks.lijnctrl.data.plan

import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.api.ApiException
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.textAtMost
import com.vknxworks.lijnctrl.data.places.PlaceKind
import com.vknxworks.lijnctrl.data.places.PlaceResult
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
private data class TPlace(
    val name: String = "",
    val stopId: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val cancelled: Boolean? = null,
)

@Serializable
private data class TLeg(
    val mode: String = "WALK",
    val from: TPlace = TPlace(),
    val to: TPlace = TPlace(),
    val duration: Long = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val realTime: Boolean = false,
    val distance: Double? = null,
    val headsign: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val cancelled: Boolean? = null,
    val intermediateStops: List<TPlace> = emptyList(),
    val legGeometry: TGeometry? = null,
)

/** A leg's shape as an encoded polyline, with the number of decimal places it was encoded with. */
@Serializable
private data class TGeometry(val points: String = "", val precision: Int? = null)

@Serializable
private data class TArea(
    val name: String = "",
    val adminLevel: Double = 0.0,
    val matched: Boolean = false,
    val default: Boolean = false,
)

/** A geocoder match: a place (shop, landmark), an address or street, or a stop. */
@Serializable
private data class TMatch(
    val type: String = "",
    val name: String = "",
    val id: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val street: String? = null,
    val houseNumber: String? = null,
    val zip: String? = null,
    val country: String? = null,
    val areas: List<TArea> = emptyList(),
)

/** One departure at a stop (`/api/v6/stoptimes`). */
@Serializable
private data class TStopTime(
    val place: TPlace = TPlace(),
    val mode: String = "",
    val realTime: Boolean = false,
    val headsign: String? = null,
    val agencyName: String? = null,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val tripId: String? = null,
    val cancelled: Boolean? = null,
    val tripCancelled: Boolean? = null,
    val tripTo: TPlace? = null,
)

@Serializable
private data class TStopTimes(val stopTimes: List<TStopTime> = emptyList(), val place: TPlace? = null)

/** A departure at another operator's stop (NMBS/SNCB, STIB/MIVB, TEC…), as Transitous gives it. */
data class OtherDeparture(
    val departure: com.vknxworks.lijnctrl.data.api.Departure,
    /** The line's number and colours, for the badge. */
    val badge: com.vknxworks.lijnctrl.data.lines.LineInfo?,
    val operator: String?,
    val track: String?,
    val mode: LegMode,
)

@Serializable
private data class TItinerary(val legs: List<TLeg> = emptyList())

@Serializable
private data class TPlan(
    val itineraries: List<TItinerary> = emptyList(),
    val direct: List<TItinerary> = emptyList(),
    /** Routes leaving after the last one here. */
    val nextPageCursor: String? = null,
    /** Routes leaving before the first one here. */
    val previousPageCursor: String? = null,
)

/**
 * Routes from Transitous (transitous.org), a free, open-source, community-run journey planner with every Belgian
 * operator (De Lijn, NMBS/SNCB, STIB/MIVB, TEC) and live delays. Only used when you turn it on in Settings: the
 * start and end of your route are then sent to api.transitous.org. Its own HTTP client can't reach anything else,
 * and it identifies itself as LijnCtrl as Transitous asks.
 */
class TransitousPlanner(base: OkHttpClient) {
    // Shares the app's connection pool and threads, but with its own rules: only Transitous, over HTTPS.
    private val http: OkHttpClient = base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "LijnCtrl/${BuildConfig.VERSION_NAME} (+https://github.com/VoidedKN0X/LijnCtrl)")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .addNetworkInterceptor(Interceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host != HOST) throw IOException(str(R.string.blocked_request_to_x, url.host))
            chain.proceed(chain.request())
        })
        .build()

    /**
     * Routes from [from] to [to], leaving at or after [time] or, with [arriveBy], arriving at or before it.
     *
     * Transitous searches a 15-minute window and, to find enough routes, widens it, so its answer can hold routes
     * that leave before the time you chose (or arrive after it). Those are dropped here, and when that leaves too
     * few, the next window is asked for (later ones when leaving at, earlier ones when arriving by). The routes come
     * back closest to your time first: soonest to leave, or (arriving by) latest to arrive.
     */
    suspend fun plan(from: PlanPoint, to: PlanPoint, time: Instant, arriveBy: Boolean): List<Journey> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, Journey>()
        var cursor: String? = null
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val plan = request(from, to, time, arriveBy, cursor)
            (plan.itineraries + plan.direct).mapNotNull { it.toJourney() }
                .filter { j -> if (arriveBy) !j.end.isAfter(time.plusSeconds(SLACK_S)) else !j.start.isBefore(time.minusSeconds(SLACK_S)) }
                .forEach { j -> found.putIfAbsent(j.signature, j) }
            if (found.size >= WANTED) break
            cursor = (if (arriveBy) plan.previousPageCursor else plan.nextPageCursor)?.takeIf { it.isNotBlank() } ?: break
        }
        val list = found.values.toList()
        if (arriveBy) list.sortedWith(compareByDescending<Journey> { it.end }.thenByDescending { it.start })
        else list.sortedWith(compareBy<Journey> { it.start }.thenBy { it.end })
    }

    private fun request(from: PlanPoint, to: PlanPoint, time: Instant, arriveBy: Boolean, cursor: String?): TPlan {
        val url = "https://$HOST/api/v6/plan".toHttpUrl().newBuilder()
            .addQueryParameter("fromPlace", "${from.latitude},${from.longitude}")
            .addQueryParameter("toPlace", "${to.latitude},${to.longitude}")
            .addQueryParameter("time", time.toString())
            .addQueryParameter("arriveBy", arriveBy.toString())
            .addQueryParameter("numItineraries", WANTED.toString())
            .addQueryParameter("detailedTransfers", "false")
            .addQueryParameter("language", phoneLanguage())
            .apply { cursor?.let { addQueryParameter("pageCursor", it) } }
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val text = r.body?.textAtMost().orEmpty()
            when {
                r.code == 429 -> throw ApiException(429, str(R.string.transitous_busy))
                !r.isSuccessful -> throw ApiException(r.code, str(R.string.transitous_error_x, r.code))
            }
            text
        }
        return DeLijnApi.JSON.decodeFromString(TPlan.serializer(), body)
    }

    /**
     * Addresses, streets, shops and landmarks matching [text], from Transitous's OpenStreetMap-based geocoder, limited
     * to results in Belgium. Only used when "Search addresses and places" is on: the text you type is then sent to Transitous.
     */
    suspend fun geocode(text: String, max: Int = 8): List<PlaceResult> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/api/v1/geocode".toHttpUrl().newBuilder()
            .addQueryParameter("text", text.trim())
            // A few extra, because results outside Belgium are left out below.
            .addQueryParameter("numResults", (max + 4).toString())
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val t = r.body?.textAtMost().orEmpty()
            when {
                r.code == 429 -> throw ApiException(429, str(R.string.transitous_busy))
                !r.isSuccessful -> throw ApiException(r.code, str(R.string.transitous_error_x, r.code))
            }
            t
        }
        DeLijnApi.JSON.decodeFromString(kotlinx.serialization.builtins.ListSerializer(TMatch.serializer()), body)
            .filter { it.lat != 0.0 && it.lon != 0.0 && (it.country == null || it.country.equals("BE", true)) }
            .mapNotNull { m -> m.toPlace() }
            .take(max)
    }

    /**
     * The next departures at a stop of any operator Transitous has (`/api/v6/stoptimes`, documented in MOTIS's
     * openapi.yaml): [stopId] is the id its geocoder gave the stop. Live times where the operator publishes them
     * (Transitous has live data for NMBS/SNCB and TEC; STIB/MIVB is timetable only). Returns the stop's name as
     * Transitous knows it and the departures, soonest first.
     */
    suspend fun stopTimes(stopId: String, count: Int = 40): Pair<String?, List<OtherDeparture>> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/api/v6/stoptimes".toHttpUrl().newBuilder()
            .addQueryParameter("stopId", stopId)
            .addQueryParameter("n", count.toString())
            .addQueryParameter("language", phoneLanguage())
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val text = r.body?.textAtMost().orEmpty()
            when {
                r.code == 429 -> throw ApiException(429, str(R.string.transitous_busy))
                !r.isSuccessful -> throw ApiException(r.code, str(R.string.transitous_error_x, r.code))
            }
            text
        }
        val res = DeLijnApi.JSON.decodeFromString(TStopTimes.serializer(), body)
        val list = res.stopTimes.mapNotNull { st ->
            // Only departures: a train or bus that ends here has no departure time.
            val scheduled = parse(st.place.scheduledDeparture) ?: parse(st.place.departure) ?: return@mapNotNull null
            val live = parse(st.place.departure)?.takeIf { st.realTime }
            val number = (st.routeShortName ?: st.displayName)?.takeIf { it.isNotBlank() }
            val cancelled = st.cancelled == true || st.tripCancelled == true
            OtherDeparture(
                departure = com.vknxworks.lijnctrl.data.api.Departure(
                    id = (st.tripId ?: "") + "@" + scheduled.epochSecond,
                    stop = null,
                    line = null,
                    direction = null,
                    trip = null,
                    serviceDate = null,
                    destination = st.headsign?.takeIf { it.isNotBlank() } ?: st.tripTo?.name.orEmpty(),
                    vias = emptyList(),
                    scheduled = scheduled,
                    predicted = live,
                    cancelled = cancelled,
                    live = st.realTime && !cancelled,
                ),
                badge = number?.let {
                    com.vknxworks.lijnctrl.data.lines.LineInfo(
                        entity = "",
                        line = "",
                        publicNumber = it,
                        foreground = st.routeTextColor?.removePrefix("#")?.takeIf { c -> c.length == 6 },
                        background = st.routeColor?.removePrefix("#")?.takeIf { c -> c.length == 6 },
                    )
                },
                operator = st.agencyName?.takeIf { it.isNotBlank() },
                track = st.place.track ?: st.place.scheduledTrack,
                mode = modeOf(st.mode),
            )
        }.sortedBy { it.departure.time }
        res.place?.name?.takeIf { it.isNotBlank() } to list
    }

    private fun TMatch.toPlace(): PlaceResult? {
        // The town: the area marked as the default, or else the most local one.
        val town = areas.firstOrNull { it.default }?.name ?: areas.maxByOrNull { it.adminLevel }?.name
        val streetLine = listOfNotNull(street, houseNumber).joinToString(" ").takeIf { it.isNotBlank() }
        return when (type.uppercase()) {
            "STOP" -> {
                val detail = listOfNotNull(zip, town).joinToString(" ").ifBlank { null }
                // De Lijn stops open the stop itself (their ids end in the stop number); other operators' stops
                // (NMBS/SNCB, STIB/MIVB, TEC…) open their departures from Transitous.
                if (id?.contains("delijn", ignoreCase = true) == true) {
                    deLijnStop(id)?.let { PlaceResult(name, detail, lat, lon, PlaceKind.STOP, it) }
                } else if (!id.isNullOrBlank()) {
                    PlaceResult(name, detail, lat, lon, PlaceKind.STOP, otherStopId = id)
                } else null
            }
            "ADDRESS" -> PlaceResult(
                name = streetLine ?: name,
                detail = listOfNotNull(zip, town).joinToString(" ").ifBlank { null },
                latitude = lat,
                longitude = lon,
                kind = if (houseNumber.isNullOrBlank()) PlaceKind.STREET else PlaceKind.ADDRESS,
            )
            else -> PlaceResult(
                name = name.ifBlank { streetLine ?: return null },
                detail = listOfNotNull(streetLine?.takeIf { it != name }, town).joinToString(", ").ifBlank { null },
                latitude = lat,
                longitude = lon,
                kind = PlaceKind.PLACE,
            )
        }
    }

    private fun TItinerary.toJourney(): Journey? {
        val out = legs.mapNotNull { it.toLeg() }
        return if (out.isEmpty()) null else Journey(out, SOURCE)
    }

    private fun TLeg.toLeg(): JourneyLeg? {
        val start = parse(startTime) ?: parse(from.departure) ?: return null
        val end = parse(endTime) ?: parse(to.arrival) ?: return null
        val m = modeOf(mode)
        val isDeLijn = agencyName?.contains("Lijn", ignoreCase = true) == true
        return JourneyLeg(
            mode = m,
            fromName = from.name,
            toName = to.name,
            start = start,
            end = end,
            scheduledStart = parse(scheduledStartTime),
            scheduledEnd = parse(scheduledEndTime),
            live = realTime,
            cancelled = cancelled == true,
            badge = if (m == LegMode.WALK) null else (routeShortName ?: displayName)?.takeIf { it.isNotBlank() },
            badgeBackground = routeColor?.removePrefix("#")?.takeIf { it.length == 6 },
            badgeForeground = routeTextColor?.removePrefix("#")?.takeIf { it.length == 6 },
            headsign = headsign,
            operator = agencyName,
            stopsBetween = intermediateStops.size,
            distanceMetres = distance?.toInt(),
            fromStop = if (isDeLijn) deLijnStop(from.stopId) else null,
            toStop = if (isDeLijn) deLijnStop(to.stopId) else null,
            track = from.track ?: from.scheduledTrack,
            path = pathOf(this),
        )
    }

    /**
     * The leg's shape from Transitous; if that's missing or doesn't start where the leg starts, the stops it
     * passes, joined by straight lines.
     */
    private fun pathOf(leg: TLeg): List<LatLon> {
        val start = LatLon(leg.from.lat, leg.from.lon)
        val g = leg.legGeometry?.points.orEmpty()
        if (g.isNotEmpty()) {
            for (p in listOfNotNull(leg.legGeometry?.precision, 6, 7, 5).distinct()) {
                val pts = decodePolyline(g, p)
                if (pts.size >= 2 && (start.latitude == 0.0 ||
                        distanceMetres(pts.first().latitude, pts.first().longitude, start.latitude, start.longitude) < 2_000)
                ) return pts
            }
        }
        return (listOf(leg.from) + leg.intermediateStops + listOf(leg.to))
            .filter { it.lat != 0.0 && it.lon != 0.0 }
            .map { LatLon(it.lat, it.lon) }
    }

    private fun parse(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** De Lijn stop ids end in the six-digit stop number; its first digit is the region. */
    private fun deLijnStop(id: String?): StopKey? {
        val number = id?.let { Regex("(\\d{6})$").find(it)?.groupValues?.get(1) } ?: return null
        return StopKey(number.take(1), number)
    }

    private fun modeOf(mode: String): LegMode = when (mode.uppercase()) {
        "WALK" -> LegMode.WALK
        "BUS", "COACH" -> LegMode.BUS
        "TRAM" -> LegMode.TRAM
        "SUBWAY", "METRO" -> LegMode.METRO
        "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_FAST_RAIL", "REGIONAL_RAIL", "SUBURBAN" -> LegMode.TRAIN
        else -> LegMode.OTHER
    }

    companion object {
        const val HOST = "api.transitous.org"
        /** How many routes to show. */
        private const val WANTED = 6
        /** At most this many requests per search (each further one looks at the next 15 minutes or more). */
        private const val MAX_PAGES = 3
        /** A route leaving a minute before your time (or arriving a minute after) still counts. */
        private const val SLACK_S = 60L
        const val SOURCE = "transitous"
        const val SOURCES_URL = "https://transitous.org/sources/"

        /** The language Transitous should answer in (stop and line names, alerts): the phone's if en/nl/fr/de, else English. */
        fun phoneLanguage(): String = java.util.Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en"
        private val LANGUAGES = setOf("en", "nl", "fr", "de")
    }
}
