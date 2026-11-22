package com.vknxworks.lijnctrl.data.live

import com.vknxworks.lijnctrl.AppContainer
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.parseLocalTime
import com.vknxworks.lijnctrl.data.plan.JourneyLeg
import java.time.Instant
import kotlin.math.abs

/**
 * What to look for in De Lijn's live feed to find the bus or tram of one ride of a planned route.
 *
 * The feed only lists the next few stops of each vehicle (4 on average, never more than about 9), so looking for
 * just the stop where you get on finds the bus only once it's a few stops away. Instead, the ride's trip is looked
 * up at that stop (the same line, leaving at the planned time), and the target is every stop of that trip with
 * its time, moved by the delay De Lijn reports at your stop: then the bus is found wherever it is on its trip.
 *
 * Falls back to just the stops where you get on and off when the trip can't be found (no De Lijn stop, the
 * departure isn't listed, or the trip lookup fails).
 */
suspend fun rideTarget(c: AppContainer, leg: JourneyLeg): LiveTarget? {
    val basic = buildMap {
        leg.fromStop?.let { put(it.number, leg.start) }
        leg.toStop?.let { put(it.number, leg.end) }
    }.takeIf { it.isNotEmpty() }?.let { LiveTarget(it, leg.trip) }
    val from = leg.fromStop ?: return basic
    return runCatching {
        val departures = c.departures.upcoming(from, 30)
        runCatching { c.lines.ensure(departures.mapNotNull { it.line }.toSet()) }
        val planned = leg.scheduledStart ?: leg.start
        val d = departures.firstOrNull { sameRide(c, it, leg, planned) } ?: return@runCatching basic
        val line = d.line ?: return@runCatching basic
        val direction = d.direction ?: return@runCatching basic
        val trip = d.trip ?: return@runCatching basic
        val date = d.serviceDate ?: return@runCatching basic
        val passages = c.api.trip(line, direction, trip, date).trips.firstOrNull()?.passages.orEmpty()
        // The delay at your stop, applied to the whole trip (the trip lookup gives timetable times).
        val delay = (d.predicted ?: d.scheduled).epochSecond - d.scheduled.epochSecond
        val stops = passages.mapNotNull { p ->
            val n = p.stopNumber ?: return@mapNotNull null
            val t = parseLocalTime(p.realtime) ?: parseLocalTime(p.scheduled)?.plusSeconds(delay) ?: return@mapNotNull null
            n to t
        }.toMap()
        if (stops.size < 2) basic else LiveTarget(basic?.stops.orEmpty() + stops, trip)
    }.getOrElse { basic }
}

/** The departure that is this ride: the same trip when the route knows it, else the same line number around the planned time. */
private fun sameRide(c: AppContainer, d: Departure, leg: JourneyLeg, planned: Instant): Boolean {
    if (leg.trip != null && leg.line != null) return d.trip == leg.trip && d.line == leg.line
    val number = d.line?.let { c.lines.get(it)?.publicNumber ?: it.line } ?: return false
    val badge = leg.badge ?: return false
    return number.equals(badge, ignoreCase = true) &&
        (abs(d.scheduled.epochSecond - planned.epochSecond) <= 180 || abs((d.predicted ?: d.scheduled).epochSecond - leg.start.epochSecond) <= 120)
}
