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
        destination = destination?.takeIf { it.isNotBlank() } ?: placeDestination?.takeIf { it.isNotBlank() } ?: destinationShort.orEmpty(),
        vias = vias.filter { it.isNotBlank() },
        scheduled = sched,
        predicted = pred,
        cancelled = cancelled,
        live = "REALTIME" in statuses && pred != null,
    )
}

/** Departures of one schedule, keyed by stop number, soonest first. */
fun ApiStopSchedule.departuresByStop(): Map<String, List<Departure>> =
    stops.associate { sp -> sp.stop.orEmpty() to sp.passages.mapNotNull { p -> p.toDeparture()?.copy(stop = sp.stop) }.sortedBy { it.time } }

fun ApiStopSchedule.departures(): List<Departure> = departuresByStop().values.flatten().sortedBy { it.time }

/** A disruption or diversion in the language the user picked, falling back to Dutch. */
data class Disruption(
    val title: String,
    val text: String,
    val start: Instant?,
    val end: Instant?,
    val lines: List<LineKey>,
)

fun ApiDisruption.localized(language: String): Disruption {
    val title = titles[language]?.takeIf { it.isNotBlank() } ?: title ?: titles["nederlands"].orEmpty()
    val text = descriptions[language]?.takeIf { it.isNotBlank() } ?: description ?: descriptions["nederlands"].orEmpty()
    return Disruption(
        title = title.trim(),
        text = text.trim(),
        start = parseLocalTime(period?.start),
        end = parseLocalTime(period?.end),
        lines = lineDirections.map { LineKey(it.entity, it.line) }.distinct(),
    )
}
