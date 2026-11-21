package com.vknxworks.lijnctrl.data.live

import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.api.ApiException
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.bytesAtMost
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import kotlin.math.abs

/**
 * A trip the app wants the vehicle of: the stops it calls at with the time it's expected there (live when known),
 * and De Lijn's trip number as a hint.
 */
data class LiveTarget(val stops: Map<String, Instant>, val tripNumber: String? = null)

/** A vehicle found for a [LiveTarget]: where it is now. */
data class LiveFix(
    val vehicleId: String?,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,
    val speed: Float?,
    /** When the vehicle reported this position. */
    val reportedAt: Instant?,
    /** How full it is (GTFS Realtime OccupancyStatus), when De Lijn's feed says. */
    val occupancy: Int? = null,
)

/**
 * Live positions of De Lijn buses and trams, from De Lijn's GTFS Realtime feed (a separate subscription on
 * data.delijn.be, with its own key). Goes through the app's own HTTP client, so it can only reach api.delijn.be.
 *
 * The feed names trips by De Lijn's GTFS ids, not the ids the rest of the API uses, so a trip is found by what
 * both agree on: the stop numbers it's heading to and when it's expected there. The feed (about 250 KB) is
 * downloaded at most once every [MIN_AGE_MS], however many trips are being followed.
 */
class LiveVehicles(private val http: OkHttpClient, private val key: () -> String?) {
    private val mutex = Mutex()
    private var cached: RtFeed? = null
    private var cachedAt = 0L

    val hasKey: Boolean get() = !key().isNullOrBlank()

    private suspend fun download(k: String, query: Map<String, String> = emptyMap()): RtFeed = withContext(Dispatchers.IO) {
        val url = "https://${DeLijnApi.HOST}/gtfs/v3/realtime".toHttpUrl().newBuilder()
            .addQueryParameter("position", "true")
            .apply { query.forEach { (n, v) -> addQueryParameter(n, v) } }
            .build()
        val req = Request.Builder().url(url).header("Ocp-Apim-Subscription-Key", k).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.bytesAtMost(com.vknxworks.lijnctrl.data.api.MAX_FEED_BYTES) ?: ByteArray(0)
            when {
                r.code == 401 || r.code == 403 -> throw ApiException(r.code, str(R.string.gtfs_key_refused))
                r.code == 429 -> throw ApiException(429, str(R.string.gtfs_busy))
                !r.isSuccessful -> throw ApiException(r.code, str(R.string.gtfs_error_x, r.code))
            }
            GtfsRealtime.parse(body)
        }
    }

    /** The whole feed, downloaded again only when the copy in memory is older than [MIN_AGE_MS]. */
    suspend fun feed(): RtFeed? {
        val k = key()?.takeIf { it.isNotBlank() } ?: return null
        return mutex.withLock {
            val now = System.currentTimeMillis()
            cached?.takeIf { now - cachedAt < MIN_AGE_MS } ?: download(k).also {
                cached = it
                cachedAt = now
            }
        }
    }

    /** Checks a key without downloading the whole feed (asks for a trip that doesn't exist). */
    suspend fun check(k: String) {
        download(k.trim(), mapOf("tripid" to "lijnctrl-check"))
    }

    /** Finds the vehicles running [targets] (null where none is found), from one download of the feed. */
    suspend fun locate(targets: List<LiveTarget>): List<LiveFix?> {
        val feed = feed() ?: return targets.map { null }
        val byTrip = feed.vehicles.filter { it.tripId != null }.associateBy { it.tripId }
        val byVehicle = feed.vehicles.filter { it.vehicleId != null }.associateBy { it.vehicleId }
        return targets.map { t ->
            val trip = match(feed.trips, t) ?: return@map null
            val v = byTrip[trip.tripId] ?: trip.vehicleId?.let { byVehicle[it] } ?: return@map null
            LiveFix(v.vehicleId ?: trip.vehicleId, v.latitude, v.longitude, v.bearing, v.speed, v.timestamp?.let(Instant::ofEpochSecond), v.occupancy)
        }
    }

    suspend fun locate(target: LiveTarget): LiveFix? = locate(listOf(target)).first()

    /**
     * Service alerts in the live feed that apply now and name [stopNumber] (the feed's stop ids are De Lijn stop
     * numbers). Empty without a key, or when the feed carries no alerts for it.
     */
    suspend fun alertsAt(stopNumber: String): List<RtAlert> {
        val feed = feed() ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        return feed.alerts.filter { stopNumber in it.stops && it.activeAt(now) }.distinctBy { it.id ?: it.header.firstOrNull()?.text }
    }

    /**
     * Whether De Lijn answers a request for one vehicle with just that vehicle (a few hundred bytes) rather than
     * the whole feed. Unknown (null) until the first [follow]; when it doesn't, following falls back to the feed.
     */
    @Volatile private var filterWorks: Boolean? = null

    /** A vehicle can be asked for on its own, so it can be followed closely ([followDelayMs]) without big downloads. */
    val canFollowFast: Boolean get() = filterWorks != false

    /** The latest position of one vehicle, asked for on its own when De Lijn allows that. */
    suspend fun follow(vehicleId: String): LiveFix? {
        val k = key()?.takeIf { it.isNotBlank() } ?: return null
        if (filterWorks == false) return feed()?.let { find(it, vehicleId) }
        val f = download(k, mapOf("vehicleid" to vehicleId))
        if (f.entityCount > 10) {
            // The filter was ignored and this is the whole feed: keep it as the cached copy and stop asking per vehicle.
            filterWorks = false
            mutex.withLock {
                cached = f
                cachedAt = System.currentTimeMillis()
            }
        } else {
            filterWorks = true
        }
        return find(f, vehicleId)
    }

    private fun find(feed: RtFeed, vehicleId: String): LiveFix? =
        feed.vehicles.firstOrNull { it.vehicleId == vehicleId }?.let {
            LiveFix(it.vehicleId, it.latitude, it.longitude, it.bearing, it.speed, it.timestamp?.let(Instant::ofEpochSecond), it.occupancy)
        }

    /**
     * The live trip that fits [t] best: the most of its stops expected within [TOLERANCE_S] of when the app
     * expects them, the closest times breaking ties. The trip number in the GTFS id (its third part) helps when
     * it matches.
     */
    /**
     * A plain-text report of why each target was or wasn't found, for the "why no live position?" check on the
     * maps: the feed itself, then per target the stops looked for, the best candidate trip and its vehicle.
     * Never contains the key. Downloads the feed afresh.
     */
    suspend fun explain(targets: List<LiveTarget>): String = buildString {
        val k = key()?.takeIf { it.isNotBlank() }
        if (k == null) {
            appendLine("GTFS key: none readable (not set, or the app is locked)")
            return@buildString
        }
        val feed = runCatching { download(k) }.getOrElse { e ->
            appendLine("Feed download failed: ${e.javaClass.simpleName}: ${e.message}")
            return@buildString
        }
        mutex.withLock { cached = feed; cachedAt = System.currentTimeMillis() }
        val now = System.currentTimeMillis() / 1000
        appendLine("Feed: ${feed.trips.size} trips, ${feed.vehicles.size} vehicles with a position, age ${feed.timestamp?.let { now - it } ?: "?"} s")
        appendLine("Vehicles with trip id: ${feed.vehicles.count { it.tripId != null }}, with vehicle id: ${feed.vehicles.count { it.vehicleId != null }}")
        appendLine("Vehicles with occupancy: ${feed.vehicles.count { it.occupancy != null || it.occupancyPercent != null }}, service alerts: ${feed.alerts.size} (${feed.alerts.count { it.stops.isNotEmpty() }} naming stops)")
        feed.trips.firstOrNull { it.stops.size >= 2 }?.let { appendLine("Sample trip: ${it.tripId.take(40)} stops ${it.stops.take(3).joinToString { s -> "${s.stopId}@${s.time?.let { t -> t - now }}s" }}") }
        val byTrip = feed.vehicles.filter { it.tripId != null }.associateBy { it.tripId }
        val byVehicle = feed.vehicles.filter { it.vehicleId != null }.associateBy { it.vehicleId }
        targets.forEachIndexed { i, t ->
            appendLine()
            val sample = t.stops.entries.sortedBy { it.value }.take(4).joinToString { "${it.key}@${it.value.epochSecond - now}s" }
            appendLine("Target ${i + 1}: ${t.stops.size} stops, trip ${t.tripNumber ?: "-"}; first: $sample")
            // Trips that list any of the target's stops at all, ignoring times.
            val anyStop = feed.trips.filter { tr -> tr.stops.any { it.stopId in t.stops } }
            appendLine("Trips listing any of these stops: ${anyStop.size}")
            var best: RtTrip? = null
            var bestHits = 0
            var bestGap = Long.MAX_VALUE
            for (tr in anyStop) {
                var hits = 0
                var gap = Long.MAX_VALUE
                for (st in tr.stops) {
                    val want = t.stops[st.stopId] ?: continue
                    val d = abs((st.time ?: continue) - want.epochSecond)
                    gap = minOf(gap, d)
                    if (d <= TOLERANCE_S) hits++
                }
                if (hits > bestHits || (hits == bestHits && gap < bestGap)) { best = tr; bestHits = hits; bestGap = gap }
            }
            if (best == null) {
                appendLine("No trip in the feed lists these stops right now")
            } else {
                appendLine("Closest trip: ${best.tripId.take(40)}, $bestHits stops within ${TOLERANCE_S / 60} min, nearest time off by $bestGap s")
                val v = byTrip[best.tripId] ?: best.vehicleId?.let { byVehicle[it] }
                appendLine(if (v != null) "Its vehicle ${v.vehicleId ?: "?"} has a position" else "Its vehicle (${best.vehicleId ?: "no id"}) has no position in the feed")
            }
            appendLine("Result: ${if (match(feed.trips, t) != null) "matched" else "not matched"}")
        }
    }

    private fun match(trips: List<RtTrip>, t: LiveTarget): RtTrip? {
        if (t.stops.isEmpty()) return null
        var best: RtTrip? = null
        var bestScore = 0.0
        for (trip in trips) {
            var hits = 0
            var diff = 0L
            for (s in trip.stops) {
                val want = t.stops[s.stopId] ?: continue
                val time = s.time ?: continue
                val d = abs(time - want.epochSecond)
                if (d <= TOLERANCE_S) {
                    hits++
                    diff += d
                }
            }
            if (hits == 0) continue
            val numberHint = t.tripNumber != null && trip.tripId.split('_').getOrNull(2) == t.tripNumber
            val score = hits + (if (numberHint) 2.0 else 0.0) - diff / (hits * TOLERANCE_S * 10.0)
            if (score > bestScore) {
                bestScore = score
                best = trip
            }
        }
        return best
    }

    companion object {
        /** The feed is downloaded at most this often. */
        const val MIN_AGE_MS = 12_000L
        /** How often screens ask for the whole feed (to find vehicles, or when one can't be asked for on its own). */
        const val REFRESH_MS = 12_000L
        /** The shortest wait between two requests for a found vehicle on its own (when its next report is overdue). */
        const val FAST_MS = 3_000L
        /** Vehicles report their position about this often. */
        const val REPORT_MS = 15_000L
        /** A little time for a new report to reach De Lijn's feed. */
        private const val REPORT_MARGIN_MS = 1_000L

        /**
         * How long to wait before asking for the followed vehicles again. Vehicles report about every
         * [REPORT_MS], so asking sooner only returns the position the app already has: the next request waits
         * until the oldest of [reportedAt] (when each followed vehicle last reported; null when unknown) is due
         * to be replaced. A report that's overdue is asked for every [FAST_MS], as before. Never longer than
         * [maxMs], whatever the phone's clock says.
         */
        fun followDelayMs(reportedAt: List<Instant?>, nowMs: Long, maxMs: Long): Long {
            if (maxMs <= FAST_MS) return FAST_MS
            val oldest = reportedAt.minOfOrNull { it?.toEpochMilli() ?: 0L } ?: return FAST_MS
            return (oldest + REPORT_MS + REPORT_MARGIN_MS - nowMs).coerceIn(FAST_MS, maxMs)
        }
        /** How often the whole feed is checked again while following, in case the bus changed trips. */
        const val RELOCATE_MS = 45_000L
        /** How far a live time may be from what the app expects and still count as the same trip. */
        const val TOLERANCE_S = 8 * 60L
        const val PORTAL_URL = "https://data.delijn.be/"
    }
}
