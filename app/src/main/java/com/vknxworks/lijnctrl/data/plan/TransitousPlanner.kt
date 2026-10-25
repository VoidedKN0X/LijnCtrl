package com.vknxworks.lijnctrl.data.plan

import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.api.ApiException
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.StopKey
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
)

@Serializable
private data class TItinerary(val legs: List<TLeg> = emptyList())

@Serializable
private data class TPlan(
    val itineraries: List<TItinerary> = emptyList(),
    val direct: List<TItinerary> = emptyList(),
)

/**
 * Routes from Transitous (transitous.org), a free, open-source, community-run journey planner with every Belgian
 * operator (De Lijn, NMBS/SNCB, STIB/MIVB, TEC) and live delays. Only used when you turn it on in Settings: the
 * start and end of your route are then sent to api.transitous.org. Its own HTTP client can't reach anything else,
 * and it identifies itself as LijnCtrl as Transitous asks.
 */
class TransitousPlanner {
    private val http: OkHttpClient = OkHttpClient.Builder()
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

    suspend fun plan(from: PlanPoint, to: PlanPoint, time: Instant, arriveBy: Boolean): List<Journey> = withContext(Dispatchers.IO) {
        val url = "https://$HOST/api/v6/plan".toHttpUrl().newBuilder()
            .addQueryParameter("fromPlace", "${from.latitude},${from.longitude}")
            .addQueryParameter("toPlace", "${to.latitude},${to.longitude}")
            .addQueryParameter("time", time.toString())
            .addQueryParameter("arriveBy", arriveBy.toString())
            .addQueryParameter("numItineraries", "6")
            .addQueryParameter("detailedTransfers", "false")
            .addQueryParameter("language", "nl")
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.code == 429 -> throw ApiException(429, str(R.string.transitous_busy))
                !r.isSuccessful -> throw ApiException(r.code, str(R.string.transitous_error_x, r.code))
            }
            text
        }
        val plan = DeLijnApi.JSON.decodeFromString(TPlan.serializer(), body)
        (plan.itineraries + plan.direct).mapNotNull { it.toJourney() }.distinctBy { it.signature }
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
        )
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
        const val SOURCE = "transitous"
        const val SOURCES_URL = "https://transitous.org/sources/"
    }
}
