package com.vknxworks.lijnctrl.alerts

import com.vknxworks.lijnctrl.alerts.AlertTiming.Step
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AlertTimingTest {
    private val t0 = Instant.parse("2026-11-13T08:00:00Z")
    private fun at(min: Long, sec: Long = 0) = t0.plusSeconds(min * 60 + sec)

    @Test fun alertTimeAndMinutesLeft() {
        assertEquals(at(25), AlertTiming.alertTime(at(30), 5))
        assertEquals(5, AlertTiming.minutesLeft(at(25), at(30)))
        assertEquals(5, AlertTiming.minutesLeft(at(25, 10), at(30))) // 4:50 rounds to 5
        assertEquals(4, AlertTiming.minutesLeft(at(25, 40), at(30))) // 4:20 rounds to 4
        assertEquals(0, AlertTiming.minutesLeft(at(31), at(30)))
    }

    @Test fun wakesUpCloserAndCloser() {
        // Bus at 09:00 (60 min away), alert 5 min before = 08:55.
        val dep = at(60)
        assertEquals(Step.CheckAt(at(40)), AlertTiming.next(t0, 5, dep)) // 15 min before the alert
        assertEquals(Step.CheckAt(at(50)), AlertTiming.next(at(40), 5, dep)) // then 5 min before
        assertEquals(Step.CheckAt(at(54)), AlertTiming.next(at(50), 5, dep)) // then 1 min before
        assertEquals(Step.CheckAt(at(55)), AlertTiming.next(at(54), 5, dep)) // then the alert itself
        assertEquals(Step.Notify(5), AlertTiming.next(at(55), 5, dep))
    }

    @Test fun skipsWakeUpsThatAreAlreadyPast() {
        // Set 8 minutes before the alert: the 15-minute wake-up is past, the 5-minute one is next.
        assertEquals(Step.CheckAt(at(3)), AlertTiming.next(t0, 2, at(10)))
        // Within the grace period: goes off now.
        assertEquals(Step.Notify(10), AlertTiming.next(at(0, -20), 10, at(10)))
    }

    @Test fun aDelayedBusMovesTheAlert() {
        // At the 5-minute wake-up the live time says the bus is 7 minutes late: the next wake-up moves with it.
        val late = at(67)
        assertEquals(Step.CheckAt(at(57)), AlertTiming.next(at(50), 5, late))
        // An early bus: due right away, with the real minutes left.
        assertEquals(Step.Notify(4), AlertTiming.next(at(50), 5, at(54)))
    }

    @Test fun lateDeliveryStillSaysTheTruth() {
        // An inexact alarm came 3 minutes after the alert time: 2 minutes left.
        assertEquals(Step.Notify(2), AlertTiming.next(at(58), 5, at(60)))
        // At the departure time: "arriving now".
        assertEquals(Step.Notify(0), AlertTiming.next(at(60, 30), 5, at(60)))
        // Long gone.
        assertEquals(Step.Left, AlertTiming.next(at(62), 5, at(60)))
    }

    @Test fun cancelledTripCancelsTheAlert() {
        assertEquals(Step.Cancelled, AlertTiming.next(t0, 5, at(60), cancelled = true))
    }

    @Test fun missingFarAheadKeepsTheKnownTime() {
        // Two hours ahead the trip may not be in the stop's list yet: no miss counted.
        val (step, misses) = AlertTiming.whenMissing(t0, 5, at(120), misses = 0)
        assertEquals(Step.CheckAt(at(100)), step)
        assertEquals(0, misses)
    }

    @Test fun missingCloseByRetriesThenGivesUp() {
        val dep = at(20)
        val (s1, m1) = AlertTiming.whenMissing(t0, 5, dep, 0)
        assertEquals(Step.CheckAt(at(1)), s1)
        assertEquals(1, m1)
        val (s2, m2) = AlertTiming.whenMissing(at(1), 5, dep, m1)
        assertEquals(Step.CheckAt(at(2)), s2)
        val (s3, _) = AlertTiming.whenMissing(at(2), 5, dep, m2)
        assertEquals(Step.Lost, s3)
    }

    @Test fun missingWhenDueOrGone() {
        // Due now but not listed this time: go off with the last known time.
        assertEquals(Step.Notify(5), AlertTiming.whenMissing(at(15), 5, at(20), 0).first)
        // Its time has passed and it's gone from the list: it left.
        assertEquals(Step.Left, AlertTiming.whenMissing(at(20), 5, at(20), 0).first)
    }

    @Test fun noDataRetriesSoonOnlyWhenClose() {
        assertEquals(Step.CheckAt(at(1)), AlertTiming.whenNoData(t0, 5, at(20)))
        // A wake-up that's already sooner than the retry stays: alert at 15:30, now 14:50.
        assertEquals(Step.CheckAt(at(15, 30)), AlertTiming.whenNoData(at(14, 50), 5, at(20, 30)))
        // Far ahead: just the next planned wake-up.
        assertEquals(Step.CheckAt(at(100)), AlertTiming.whenNoData(t0, 5, at(120)))
        // Due: goes off with the last known time.
        assertEquals(Step.Notify(5), AlertTiming.whenNoData(at(15), 5, at(20)))
    }
}
