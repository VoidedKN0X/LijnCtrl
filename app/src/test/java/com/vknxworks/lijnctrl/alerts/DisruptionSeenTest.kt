package com.vknxworks.lijnctrl.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisruptionSeenTest {
    private val stopA = DisruptionAlerts.stopSource("3_101")
    private val stopB = DisruptionAlerts.stopSource("3_1010")
    private val line = DisruptionAlerts.lineSource("3_45")

    @Test
    fun allAnsweredKeepsOnlyWhatIsStillGoingOn() {
        val prev = setOf("${stopA}1", "${line}2")
        val next = DisruptionAlerts.nextSeen(prev, setOf("${stopA}1"), emptySet(), anySucceeded = true)
        assertEquals(setOf("${stopA}1"), next)
    }

    @Test
    fun failedStopKeepsWhatWasToldAboutIt() {
        val prev = setOf("${stopA}1", "${stopB}7", "${line}2")
        // stop A failed this time; stop B and the line answered (the line's disruption has ended).
        val next = DisruptionAlerts.nextSeen(prev, setOf("${stopB}7", "${stopB}8"), setOf(stopA), anySucceeded = true)
        assertEquals(setOf("${stopA}1", "${stopB}7", "${stopB}8"), next)
    }

    @Test
    fun aStopWhoseNumberStartsTheSameIsNotKept() {
        // "3_101" failing must not keep "3_1010"'s entries: the prefix ends with a colon.
        val prev = setOf("${stopB}7")
        val next = DisruptionAlerts.nextSeen(prev, emptySet(), setOf(stopA), anySucceeded = true)
        assertEquals(emptySet<String>(), next)
    }

    @Test
    fun failedLineKeepsWhatWasToldAboutIt() {
        val prev = setOf("${line}2", "${stopA}1")
        val next = DisruptionAlerts.nextSeen(prev, setOf("${stopA}1"), setOf(line), anySucceeded = true)
        assertEquals(setOf("${line}2", "${stopA}1"), next)
    }

    @Test
    fun nothingAnsweredChangesNothing() {
        assertNull(DisruptionAlerts.nextSeen(setOf("${stopA}1"), emptySet(), setOf(stopA, line), anySucceeded = false))
    }
}
