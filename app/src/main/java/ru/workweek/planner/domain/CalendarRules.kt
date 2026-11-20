package ru.workweek.planner.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object CalendarRules {
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    private val inputFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun isSupported(date: LocalDate): Boolean = date >= MIN_DATE && date <= MAX_DATE
    fun clamp(date: LocalDate): LocalDate = date.coerceIn(MIN_DATE, MAX_DATE)
    fun weekStart(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)
    fun weekDates(date: LocalDate): List<LocalDate> = (0L..6L).map { weekStart(date).plusDays(it) }
    fun canMoveWeek(selected: LocalDate, direction: Int): Boolean {
        val next = weekStart(selected).plusWeeks(direction.toLong())
        return next >= weekStart(MIN_DATE) && next <= weekStart(MAX_DATE)
    }
    fun moveWeek(selected: LocalDate, direction: Int): LocalDate =
        if (canMoveWeek(selected, direction)) clamp(selected.plusWeeks(direction.toLong())) else selected

    fun parseDate(text: String): LocalDate? = runCatching {
        require(Regex("\\d{2}\\.\\d{2}\\.\\d{4}").matches(text))
        LocalDate.parse(text, inputFormat).also { require(isSupported(it)) }
    }.getOrNull()

    fun formatDate(date: LocalDate): String = date.format(inputFormat)
    fun today(clock: Clock, zone: ZoneId): LocalDate = LocalDate.now(clock.withZone(zone))
}
