package ru.workweek.planner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import ru.workweek.planner.domain.CalendarRules

@Entity(tableName = "entries", indices = [Index("plannedDate")])
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String = "",
    val plannedDate: LocalDate,
    val localTime: LocalTime? = null,
    val completed: Boolean = false,
    val remindOnPreviousDay: Boolean = true,
    val createdAtUtc: Instant = Instant.now(),
    val updatedAtUtc: Instant = createdAtUtc,
    val searchTitle: String = "",
    val searchBody: String = "",
)

data class DaySummary(val date: LocalDate, val count: Int, val first: Entry?)
data class DayCount(val plannedDate: LocalDate, val count: Int)

object EntryRules {
    private fun unicodeSpace(char: Char): Boolean = char.isWhitespace() || Character.isSpaceChar(char)
    fun cleanTitle(title: String): String = title.replace(Regex("[\\r\\n\\u0085\\u2028\\u2029]"), " ")
        .trim(::unicodeSpace)

    fun errors(entry: Entry): Map<String, String> = buildMap {
        val title = cleanTitle(entry.title)
        if (title.isEmpty()) put("title", "Введите название")
        else if (title.codePointCount(0, title.length) > 160) put("title", "Не более 160 символов")
        if (entry.body.codePointCount(0, entry.body.length) > 10_000) put("body", "Не более 10 000 символов")
        if (!CalendarRules.isSupported(entry.plannedDate)) put("date", "Дата должна быть в пределах 1900–2100")
        if (entry.localTime?.second != null && (entry.localTime.second != 0 || entry.localTime.nano != 0))
            put("time", "Укажите время с точностью до минуты")
    }
}
