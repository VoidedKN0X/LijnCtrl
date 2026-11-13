package com.vknxworks.lijnctrl.alerts

import java.time.Duration
import java.time.Instant

/**
 * When a "your bus leaves in X minutes" alert looks again and when it goes off. Plain Kotlin (no Android), so the
 * timing can be unit-tested.
 *
 * The alert is due [minutesBefore] minutes before the departure (the live time when De Lijn has one, else the
 * timetable). Before that, it wakes up a few times on the way, each time a little closer (15, 5 and 1 minute before
 * the alert), refreshes the departure's live time and moves the alert with it. Without exact alarms Android may
 * deliver a wake-up a little late; the notification always says how many minutes are really left.
 */
object AlertTiming {
    /** Wake-ups before the alert time, farthest first. */
    val LEADS: List<Duration> = listOf(Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofMinutes(1))

    /** Close enough to the alert time to go off now rather than wake up again. */
    val GRACE: Duration = Duration.ofSeconds(30)

    /** The bus is taken to have left this long after its time. */
    val GONE_AFTER: Duration = Duration.ofSeconds(60)

    /**
     * A trip expected within this time must be in the stop's list of upcoming departures; further ahead it may
     * simply not be listed yet, so not finding it then isn't a reason to give up.
     */
    val LISTED_WITHIN: Duration = Duration.ofMinutes(25)

    /** Look-ups in a row that didn't find the trip (while it should be listed) before the alert gives up. */
    const val MAX_MISSES = 3

    /** Retry soon when the trip wasn't found or the refresh failed. */
    val RETRY: Duration = Duration.ofMinutes(1)

    sealed interface Step {
        /** Go off now: the bus leaves in [minutesLeft] minutes. */
        data class Notify(val minutesLeft: Int) : Step
        /** Look again at [at]. */
        data class CheckAt(val at: Instant) : Step
        data object Cancelled : Step
        data object Left : Step
        /** The trip can't be found any more. */
        data object Lost : Step
    }

    fun alertTime(departure: Instant, minutesBefore: Int): Instant = departure.minusSeconds(minutesBefore * 60L)

    /** Whole minutes from [now] to [departure], rounded, never below 0. */
    fun minutesLeft(now: Instant, departure: Instant): Int {
        val s = Duration.between(now, departure).seconds
        return if (s <= 0) 0 else ((s + 30) / 60).toInt()
    }

    /** The next step, knowing the departure's current time ([departure]) and whether it's cancelled. */
    fun next(now: Instant, minutesBefore: Int, departure: Instant, cancelled: Boolean = false): Step {
        if (cancelled) return Step.Cancelled
        if (now.isAfter(departure.plus(GONE_AFTER))) return Step.Left
        val alert = alertTime(departure, minutesBefore)
        if (!now.isBefore(alert.minus(GRACE))) return Step.Notify(minutesLeft(now, departure))
        // The farthest wake-up that's still ahead (with a little margin), else the alert itself.
        val wake = LEADS.map { alert.minus(it) }.firstOrNull { it.isAfter(now.plus(GRACE)) } ?: alert
        return Step.CheckAt(wake)
    }

    /**
     * The trip wasn't in the stop's list. [lastKnown] is its last known time, [misses] how many look-ups in a row
     * missed it before this one. Returns the step and the new miss count.
     */
    fun whenMissing(now: Instant, minutesBefore: Int, lastKnown: Instant, misses: Int): Pair<Step, Int> {
        val step = next(now, minutesBefore, lastKnown)
        // Not listed yet because it's far ahead: carry on with the time we know.
        if (Duration.between(now, lastKnown) > LISTED_WITHIN) return step to 0
        // Its time has passed and it's no longer listed: it has left.
        if (!now.isBefore(lastKnown.minus(GONE_AFTER))) return Step.Left to misses + 1
        // Due anyway: go off with the last known time rather than wait for another look-up.
        if (step is Step.Notify) return step to misses + 1
        val m = misses + 1
        if (m >= MAX_MISSES) return Step.Lost to m
        return Step.CheckAt(earliest(now.plus(RETRY), (step as? Step.CheckAt)?.at)) to m
    }

    /**
     * The refresh failed (offline, for example): keep going with the last known time, and when the departure is
     * close, try again soon. Without a way to refresh at all (the app is locked), use [next] instead.
     */
    fun whenNoData(now: Instant, minutesBefore: Int, lastKnown: Instant): Step {
        val step = next(now, minutesBefore, lastKnown)
        if (step !is Step.CheckAt || Duration.between(now, lastKnown) > LISTED_WITHIN) return step
        return Step.CheckAt(earliest(now.plus(RETRY), step.at))
    }

    private fun earliest(a: Instant, b: Instant?): Instant = if (b != null && b.isBefore(a)) b else a
}
