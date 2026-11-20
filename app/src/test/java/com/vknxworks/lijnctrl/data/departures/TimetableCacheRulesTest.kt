package com.vknxworks.lijnctrl.data.departures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableCacheRulesTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun freshTimetablesOfYesterdayTodayAndLaterAreUsed() {
        assertTrue(TimetableCacheRules.usable(now, today, now, today))
        assertTrue(TimetableCacheRules.usable(now - hour, today, now, today))
        assertTrue(TimetableCacheRules.usable(now - hour, today.minusDays(1), now, today))
        assertTrue(TimetableCacheRules.usable(now - hour, today.plusDays(1), now, today))
        assertTrue(TimetableCacheRules.usable(now - hour, today.plusDays(30), now, today))
        assertTrue(TimetableCacheRules.usable(now - TimetableCacheRules.MAX_AGE_MS, today, now, today))
    }

    @Test
    fun oldTimetablesAreNotUsed() {
        assertFalse(TimetableCacheRules.usable(now - TimetableCacheRules.MAX_AGE_MS - 1, today, now, today))
        assertFalse(TimetableCacheRules.usable(now - 24 * hour, today, now, today))
        assertFalse(TimetableCacheRules.usable(0L, today, now, today))
    }

    @Test
    fun daysBeforeYesterdayAreNotUsed() {
        assertFalse(TimetableCacheRules.usable(now - hour, today.minusDays(2), now, today))
        assertFalse(TimetableCacheRules.usable(now, today.minusDays(30), now, today))
    }

    @Test
    fun aTimetableFromTheFutureIsNotUsed() {
        // The phone's clock was set back after it was fetched.
        assertFalse(TimetableCacheRules.usable(now + 1, today, now, today))
        assertFalse(TimetableCacheRules.usable(now + 10 * hour, today, now, today))
    }
}
