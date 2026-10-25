package com.vknxworks.lijnctrl.data.plan

import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import java.time.Instant
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One end of a route: a place, a stop, or "where I am now" (looked up when you plan). */
data class PlanPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Set when the point is a De Lijn stop. */
    val stop: StopKey? = null,
    /** Shown under the name, like the town. */
    val detail: String? = null,
    val isMyLocation: Boolean = false,
)

enum class LegMode { WALK, BUS, TRAM, METRO, TRAIN, OTHER }

/** One part of a journey: a walk, or a ride on one bus, tram or train. */
data class JourneyLeg(
    val mode: LegMode,
    val fromName: String,
    val toName: String,
    val start: Instant,
    val end: Instant,
    val scheduledStart: Instant? = null,
    val scheduledEnd: Instant? = null,
    /** The times come from live data (Transitous), not just the timetable. */
    val live: Boolean = false,
    val cancelled: Boolean = false,
    /** What's on the vehicle: "71", "T1", "IC". */
    val badge: String? = null,
    /** Hex colours without '#', when the planner knows them. */
    val badgeBackground: String? = null,
    val badgeForeground: String? = null,
    val headsign: String? = null,
    /** Who runs it ("De Lijn", "NMBS/SNCB"), shown for other operators. */
    val operator: String? = null,
    /** Stops the vehicle calls at between getting on and getting off. */
    val stopsBetween: Int = 0,
    val distanceMetres: Int? = null,
    /** De Lijn details, so the leg can open the stop, line and trip pages. */
    val line: LineKey? = null,
    val direction: String? = null,
    val trip: String? = null,
    val serviceDate: LocalDate? = null,
    val fromStop: StopKey? = null,
    val toStop: StopKey? = null,
    val track: String? = null,
) {
    val isWalk: Boolean get() = mode == LegMode.WALK
    val minutes: Long get() = ((end.epochSecond - start.epochSecond + 59) / 60).coerceAtLeast(1)
}

/** A whole route from A to B. */
data class Journey(
    val legs: List<JourneyLeg>,
    /** Which planner made it: "builtin" or "transitous". */
    val source: String,
) {
    val start: Instant get() = legs.first().start
    val end: Instant get() = legs.last().end
    val transfers: Int get() = (legs.count { !it.isWalk } - 1).coerceAtLeast(0)
    val minutes: Long get() = ((end.epochSecond - start.epochSecond + 59) / 60).coerceAtLeast(1)
    val walkOnly: Boolean get() = legs.all { it.isWalk }

    /** Two journeys on the same vehicles at the same times are the same journey. */
    val signature: String
        get() = legs.filter { !it.isWalk }.joinToString("|") { "${it.badge}_${it.start.epochSecond}_${it.end.epochSecond}" }
}

/** Straight-line distance in metres. */
fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** Walking time for a straight-line distance: streets add about a quarter, at an easy 4.7 km/h. */
fun walkSeconds(metres: Double): Long = (metres * 1.25 / 1.3).toLong()
