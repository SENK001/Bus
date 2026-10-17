package com.senk.bus.data

import com.senk.bus.data.entity.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [NextDeparture]. Run with `./gradlew test`.
 */
class NextDepartureTest {

    private fun at(time: String): Schedule = Schedule().apply { departureTime = time }

    private fun list(vararg times: String): List<Schedule> = times.map { at(it) }

    @Test
    fun find_nullOrEmpty_returnsNull() {
        assertNull(NextDeparture.find("08:00", null))
        assertNull(NextDeparture.find("08:00", ArrayList()))
    }

    @Test
    fun find_beforeFirstDeparture_returnsFirst() {
        val schedules = list("08:30", "12:00", "18:00")
        val next = NextDeparture.find("07:00", schedules)
        assertNotNull(next)
        assertEquals("08:30", next!!.time)
        assertEquals(90, next.remainingMinutes)
        assertSame(schedules[0], next.schedule)
    }

    @Test
    fun find_exactDepartureTime_returnsZeroMinutes() {
        val next = NextDeparture.find("08:30", list("08:30", "12:00"))
        assertNotNull(next)
        assertEquals("08:30", next!!.time)
        assertEquals(0, next.remainingMinutes)
    }

    @Test
    fun find_betweenDepartures_returnsNextOne() {
        val next = NextDeparture.find("09:15", list("08:30", "12:00", "18:00"))
        assertNotNull(next)
        assertEquals("12:00", next!!.time)
        assertEquals(165, next.remainingMinutes)
    }

    @Test
    fun find_afterLastDeparture_wrapsToTomorrowFirst() {
        val next = NextDeparture.find("23:00", list("08:30", "12:00", "18:00"))
        assertNotNull(next)
        assertEquals("08:30", next!!.time)
        // 23:00 -> 08:30 next day
        assertEquals(570, next.remainingMinutes)
    }

    @Test
    fun find_midnightWrap_isHandled() {
        // Last departure 23:50, first 00:10: at 23:55 the next one is 00:10 tomorrow.
        var next = NextDeparture.find("23:55", list("00:10", "23:50"))
        assertNotNull(next)
        assertEquals("00:10", next!!.time)
        assertEquals(15, next.remainingMinutes)

        // At 00:05 the next one is today's 00:10 (not the later 23:50).
        next = NextDeparture.find("00:05", list("00:10", "23:50"))
        assertNotNull(next)
        assertEquals("00:10", next!!.time)
        assertEquals(5, next.remainingMinutes)
    }

    @Test
    fun find_skipsInvalidEntries() {
        val schedules = list("abc", "25:99", "08:30", "12:00")
        val next = NextDeparture.find("07:00", schedules)
        assertNotNull(next)
        assertEquals("08:30", next!!.time)

        assertNull(NextDeparture.find("07:00", list("abc", "25:99")))
    }

    @Test
    fun find_invalidNow_fallsBackToCurrentTime() {
        // Should not crash; result is either today's upcoming or tomorrow's first.
        val next = NextDeparture.find("not-a-time", list("00:00"))
        assertNotNull(next)
        assertEquals("00:00", next!!.time)
        assertTrue(next.remainingMinutes >= 0 && next.remainingMinutes < 24 * 60)
    }

    @Test
    fun parseMinutes_validAndInvalid() {
        assertEquals(0, NextDeparture.parseMinutes("00:00"))
        assertEquals(1439, NextDeparture.parseMinutes("23:59"))
        assertEquals(510, NextDeparture.parseMinutes("08:30"))
        assertEquals(-1, NextDeparture.parseMinutes(null))
        assertEquals(-1, NextDeparture.parseMinutes(""))
        assertEquals(-1, NextDeparture.parseMinutes("8:30"))
        assertEquals(-1, NextDeparture.parseMinutes("24:00"))
        assertEquals(-1, NextDeparture.parseMinutes("12:60"))
        assertEquals(-1, NextDeparture.parseMinutes("ab:cd"))
    }

    @Test
    fun formatMinutes_wrapsAndPads() {
        assertEquals("00:00", NextDeparture.format(0))
        assertEquals("08:05", NextDeparture.format(485))
        assertEquals("23:59", NextDeparture.format(1439))
        assertEquals("00:00", NextDeparture.format(1440))
        assertEquals("00:10", NextDeparture.format(-1430))
    }

    @Test
    fun isValid_acceptsOnlyStrictTimes() {
        assertTrue(NextDeparture.isValid("00:00"))
        assertTrue(NextDeparture.isValid("23:59"))
        assertFalse(NextDeparture.isValid("24:00"))
        assertFalse(NextDeparture.isValid("8:30"))
        assertFalse(NextDeparture.isValid("08:5"))
        assertFalse(NextDeparture.isValid(null))
    }

    @Test
    fun minutesUntil_wrapsAcrossMidnight() {
        assertEquals(20, NextDeparture.minutesUntil("23:50", "00:10"))
        assertEquals(0, NextDeparture.minutesUntil("08:30", "08:30"))
        assertEquals(1439, NextDeparture.minutesUntil("08:30", "08:29"))
        assertEquals(-1, NextDeparture.minutesUntil("bad", "08:30"))
    }

    @Test
    fun find_unsortedInputStillPicksEarliestUpcoming() {
        // The DAO normally sorts, but the helper should not return a later time when an earlier
        // upcoming one exists.
        val schedules = listOf(at("18:00"), at("12:00"), at("08:30"))
        val next = NextDeparture.find("09:00", schedules)
        assertNotNull(next)
        assertEquals("12:00", next!!.time)
    }
}
