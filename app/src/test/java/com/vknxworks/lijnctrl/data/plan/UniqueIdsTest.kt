package com.vknxworks.lijnctrl.data.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueIdsTest {
    @Test
    fun uniqueIdsStayAsTheyAre() {
        assertEquals(listOf("a@1", "b@1", "a@2"), uniqueIds(listOf("a@1", "b@1", "a@2")))
    }

    @Test
    fun repeatsGetANumber() {
        // Two departures without a trip id in the same minute, and a loop trip calling twice in one second.
        assertEquals(listOf("@60", "@60#2", "t@5", "t@5#2", "@60#3"), uniqueIds(listOf("@60", "@60", "t@5", "t@5", "@60")))
    }

    @Test
    fun aNumberedIdThatAlreadyExistsIsSkipped() {
        assertEquals(listOf("x#2", "x", "x#3"), uniqueIds(listOf("x#2", "x", "x")))
    }
}
