package ru.workweek.planner.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarRulesTest {
    @Test fun CAL_01_currentWeek() {
        val date = LocalDate.of(2026, 11, 20)
        assertEquals(LocalDate.of(2026, 11, 16), CalendarRules.weekStart(date))
        assertEquals((16..22).toList(), CalendarRules.weekDates(date).map { it.dayOfMonth })
    }

    @Test fun CAL_02_yearAndLeapBoundaries() {
        assertEquals(LocalDate.of(2026, 12, 28), CalendarRules.weekStart(LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2027, 1, 3), CalendarRules.weekDates(LocalDate.of(2026, 12, 31)).last())
        assertEquals(LocalDate.of(2028, 2, 28), CalendarRules.weekStart(LocalDate.of(2028, 2, 29)))
        assertEquals(LocalDate.of(2028, 3, 5), CalendarRules.weekDates(LocalDate.of(2028, 2, 29)).last())
    }

    @Test fun CAL_03_navigationPreservesWeekday() {
        val original = LocalDate.of(2026, 11, 20)
        var selected = original
        repeat(100) { selected = CalendarRules.moveWeek(selected, 1) }
        assertEquals(original.plusWeeks(100), selected)
        repeat(100) { selected = CalendarRules.moveWeek(selected, -1) }
        assertEquals(original, selected)
    }

    @Test fun CAL_05_strictManualDate() {
        listOf("31.02.2027", "29.02.2027", "1.01.2027", "01.01.", "01.01.1899", "01.01.2101").forEach {
            assertNull(it, CalendarRules.parseDate(it))
        }
        assertEquals(LocalDate.of(2028, 2, 29), CalendarRules.parseDate("29.02.2028"))
    }

    @Test fun CAL_06_midnightUsesInjectedClock() {
        val zone = ZoneId.of("Europe/Moscow")
        val before = Clock.fixed(Instant.parse("2026-11-22T20:59:59Z"), zone)
        val after = Clock.fixed(Instant.parse("2026-11-22T21:00:00Z"), zone)
        assertEquals(LocalDate.of(2026, 11, 22), CalendarRules.today(before, zone))
        assertEquals(LocalDate.of(2026, 11, 23), CalendarRules.today(after, zone))
    }

    @Test fun CAL_07_localDateDoesNotConvertTimeZones() {
        val entered = "21.11.2026"
        listOf("Etc/GMT+8", "Pacific/Kiritimati").forEach {
            assertEquals(LocalDate.of(2026, 11, 21), CalendarRules.parseDate(entered))
        }
    }

    @Test fun CAL_08_boundariesClampSelectionAndKeepSevenDays() {
        val lastWeek = CalendarRules.weekDates(CalendarRules.MAX_DATE)
        assertEquals(7, lastWeek.size)
        assertEquals(LocalDate.of(2101, 1, 2), lastWeek.last())
        assertFalse(CalendarRules.canMoveWeek(CalendarRules.MAX_DATE, 1))
        assertFalse(CalendarRules.canMoveWeek(CalendarRules.MIN_DATE, -1))
        assertEquals(CalendarRules.MAX_DATE, CalendarRules.moveWeek(LocalDate.of(2100, 12, 26), 1))
    }
}
