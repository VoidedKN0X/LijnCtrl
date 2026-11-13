package com.vknxworks.lijnctrl.data.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** De Lijn sends local times without an offset; they're Belgian time. */
val BRUSSELS: ZoneId = ZoneId.of("Europe/Brussels")

fun parseLocalTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        if (value.length == 10) LocalDate.parse(value).atStartOfDay(BRUSSELS).toInstant()
        else LocalDateTime.parse(value.take(19)).atZone(BRUSSELS).toInstant()
    }.getOrNull()
}

/** A departure as the app shows it. */
data class Departure(
    val id: String,
    val stop: String?,
    val line: LineKey?,
    val direction: String?,
    /** The trip (rit) number, to open the trip page. */
    val trip: String?,
    /** The day the trip belongs to (a trip after midnight still belongs to the day before). */
    val serviceDate: LocalDate?,
    val destination: String,
    val vias: List<String>,
    val scheduled: Instant,
    /** The live prediction, or null when De Lijn has none for this trip. */
    val predicted: Instant?,
    val cancelled: Boolean,
    /** De Lijn tracks this vehicle live right now. */
    val live: Boolean,
) {
    /** When it actually leaves: the prediction if there is one, otherwise the timetable. */
    val time: Instant get() = predicted ?: scheduled

    /** Matches the same passage in the live feed and the timetable. */
    val matchKey: String get() = if (line != null && trip != null) "${line}_${direction}_${trip}_$stop" else id

    /** Minutes late (positive) or early (negative), rounded; null without a prediction. */
    val delayMinutes: Long?
        get() = predicted?.let { Math.round((it.toEpochMilli() - scheduled.toEpochMilli()) / 60_000.0) }
}

fun ApiPassage.toDeparture(): Departure? {
    val sched = parseLocalTime(scheduled) ?: return null
    val statuses = statuses.map { it.uppercase() }
    // Already left: not a departure any more.
    if ("VERSTREKEN" in statuses) return null
    val pred = parseLocalTime(realtime)
    val cancelled = "GESCHRAPT" in statuses
    return Departure(
        id = id ?: "${entity}_${line}_${trip}_$scheduled",
        stop = null,
        line = if (entity != null && line != null) LineKey(entity, line) else null,
        direction = direction,
        trip = trip,
        serviceDate = serviceDateOf(id) ?: runCatching { java.time.LocalDateTime.parse(scheduled!!.take(19)).toLocalDate() }.getOrNull(),
        destination = destination?.takeIf { it.isNotBlank() } ?: placeDestination?.takeIf { it.isNotBlank() } ?: destinationShort.orEmpty(),
        vias = vias.filter { it.isNotBlank() },
        scheduled = sched,
        predicted = pred,
        cancelled = cancelled,
        live = "REALTIME" in statuses && pred != null,
    )
}

/** doorkomstId starts with the service day: "2026-10-11_2004_234_16_204911". */
fun serviceDateOf(id: String?): LocalDate? =
    id?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * The live feed only covers the next stretch of time and only trips De Lijn is tracking, so on its own it can
 * look empty at quieter stops. This adds the timetable's departures that the live feed doesn't mention,
 * leaving out ones that already left, and keeps the soonest [max].
 */
fun mergeDepartures(live: List<Departure>, timetable: List<Departure>, now: java.time.Instant, max: Int): List<Departure> {
    val liveKeys = live.flatMap { listOf(it.id, it.matchKey) }.toSet()
    val cutoff = now.minusSeconds(60)
    val extra = timetable.filter { it.id !in liveKeys && it.matchKey !in liveKeys && it.scheduled.isAfter(cutoff) }
    return (live.filter { it.cancelled || it.time.isAfter(cutoff) } + extra).sortedBy { it.time }.take(max)
}

/** Departures of one schedule, keyed by stop number, soonest first. */
fun ApiStopSchedule.departuresByStop(): Map<String, List<Departure>> =
    stops.associate { sp -> sp.stop.orEmpty() to sp.passages.mapNotNull { p -> p.toDeparture()?.copy(stop = sp.stop) }.sortedBy { it.time } }

fun ApiStopSchedule.departures(): List<Departure> = departuresByStop().values.flatten().sortedBy { it.time }

/** One line direction a disruption is about; [direction] (HEEN / TERUG) can be missing. */
data class DisruptionLine(val line: LineKey, val direction: String?)

/** A disruption or diversion in the language the user picked, falling back to Dutch. */
data class Disruption(
    val title: String,
    val text: String,
    val start: Instant?,
    val end: Instant?,
    val lines: List<LineKey>,
    /** The line directions De Lijn says it affects; empty means the whole stop (or it didn't say). */
    val directions: List<DisruptionLine> = emptyList(),
) {
    /** Same message, whichever list (stop, line, storing or omleiding) it came from. */
    val identity: String get() = "${title.lowercase()}|${text.lowercase()}"

    /** It's about [line] (in [direction], when both sides say which way). */
    fun affectsLine(line: LineKey, direction: String?): Boolean = directions.any {
        it.line == line && (it.direction.isNullOrBlank() || direction.isNullOrBlank() || it.direction.equals(direction, ignoreCase = true))
    }

    /**
     * A stop disruption affects a departure from that stop when it names the departure's line (and direction),
     * or when it names no lines at all (then it's about the stop itself, so every bus or tram there).
     */
    fun affects(d: Departure): Boolean {
        if (directions.isEmpty()) return lines.isEmpty()
        val line = d.line ?: return false
        return affectsLine(line, d.direction)
    }
}

fun ApiDisruption.localized(language: String): Disruption {
    val title = titles[language]?.takeIf { it.isNotBlank() } ?: title ?: titles["nederlands"].orEmpty()
    val text = descriptions[language]?.takeIf { it.isNotBlank() } ?: description ?: descriptions["nederlands"].orEmpty()
    return Disruption(
        title = title.trim(),
        text = text.trim(),
        start = parseLocalTime(period?.start),
        end = parseLocalTime(period?.end),
        lines = lineDirections.map { LineKey(it.entity, it.line) }.distinct(),
        directions = lineDirections.map { DisruptionLine(LineKey(it.entity, it.line), it.direction) }.distinct(),
    )
}
