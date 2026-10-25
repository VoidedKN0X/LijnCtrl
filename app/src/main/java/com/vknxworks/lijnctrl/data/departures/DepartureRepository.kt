package com.vknxworks.lijnctrl.data.departures

import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.departuresByStop
import com.vknxworks.lijnctrl.data.api.mergeDepartures
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Upcoming departures at a stop: De Lijn's live feed, topped up with the timetable. The live feed alone only
 * covers the next stretch of time and the trips De Lijn tracks, which made quieter stops (and lines without
 * live data, such as Flex and night buses) look empty. Timetables are fetched once per stop and day and kept
 * in memory.
 */
class DepartureRepository(private val api: DeLijnApi) {
    private val mutex = Mutex()
    private val timetables = mutableMapOf<Pair<StopKey, LocalDate>, List<Departure>>()

    /** The day's timetable at [stop], from memory when it was already fetched. */
    suspend fun timetable(stop: StopKey, date: LocalDate): List<Departure> {
        mutex.withLock { timetables[stop to date] }?.let { return it }
        val schedule = api.timetable(stop, date)
        val list = schedule.departuresByStop()[stop.number] ?: schedule.departuresByStop().values.flatten()
        mutex.withLock {
            // Keep it small: only today's and tomorrow's timetables are useful.
            val today = LocalDate.now(BRUSSELS)
            timetables.keys.removeAll { it.second.isBefore(today.minusDays(1)) }
            timetables[stop to date] = list
        }
        return list
    }

    /**
     * Timetable departures from now on: today's, plus tomorrow's late in the evening or when today has fewer
     * than [max] left. A failed timetable just means fewer extra departures, never an error.
     */
    suspend fun upcomingTimetable(stop: StopKey, now: Instant, max: Int): List<Departure> {
        val today = LocalDate.now(BRUSSELS)
        // Trips just after midnight still belong to yesterday's service day.
        val yesterday = if (LocalTime.now(BRUSSELS).isBefore(LocalTime.of(4, 0))) {
            runCatching { timetable(stop, today.minusDays(1)) }.getOrDefault(emptyList())
        } else emptyList()
        val todays = runCatching { timetable(stop, today) }.getOrDefault(emptyList())
        val ahead = (yesterday + todays).filter { it.scheduled.isAfter(now.minusSeconds(60)) }
        val late = LocalTime.now(BRUSSELS).isAfter(LocalTime.of(21, 0))
        val tomorrow = if (ahead.size < max || late) runCatching { timetable(stop, today.plusDays(1)) }.getOrDefault(emptyList()) else emptyList()
        return (ahead + tomorrow).sortedBy { it.scheduled }
    }

    /** The next [max] departures at one stop, live where possible. If the live feed fails but the timetable works, the timetable is shown; only when both fail is it an error. */
    suspend fun upcoming(stop: StopKey, max: Int): List<Departure> {
        val now = Instant.now()
        val live = runCatching {
            api.realtime(stop, max = max).let { s -> s.departuresByStop()[stop.number] ?: s.departuresByStop().values.flatten() }
        }
        val planned = upcomingTimetable(stop, now, max)
        if (live.isFailure && planned.isEmpty()) throw live.exceptionOrNull()!!
        return mergeDepartures(live.getOrDefault(emptyList()), planned, now, max)
    }

    /** Tops up live departures already fetched (for example in one call for several stops) with the timetable. */
    suspend fun topUp(stop: StopKey, live: List<Departure>, max: Int): List<Departure> {
        val now = Instant.now()
        return mergeDepartures(live, upcomingTimetable(stop, now, max), now, max)
    }

    fun clear() {
        timetables.clear()
    }
}
