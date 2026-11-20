package com.vknxworks.lijnctrl.data.departures

import java.time.LocalDate

/** When a day timetable kept on the phone may still be used. Plain rules, so they can be tested on their own. */
object TimetableCacheRules {
    /** A kept timetable is used for this long after it was fetched; after that De Lijn is asked again. */
    const val MAX_AGE_MS = 6L * 60 * 60 * 1000

    /** The file is never read when it's bigger than this (it's only a cache: it's deleted and built up again). */
    const val MAX_FILE_BYTES = 8L * 1024 * 1024

    /**
     * Whether the timetable of [date], fetched at [fetchedAtMs], is still good at [nowMs] ([today] in Belgium):
     * not older than [MAX_AGE_MS], not from the future (the phone's clock was set back), and for yesterday (its
     * trips after midnight still run), today or a later day.
     */
    fun usable(fetchedAtMs: Long, date: LocalDate, nowMs: Long, today: LocalDate): Boolean {
        val age = nowMs - fetchedAtMs
        return age in 0..MAX_AGE_MS && !date.isBefore(today.minusDays(1))
    }
}
