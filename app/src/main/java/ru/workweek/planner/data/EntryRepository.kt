package ru.workweek.planner.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.workweek.planner.domain.CalendarRules
import ru.workweek.planner.domain.SearchRules

class EntryRepository(private val db: PlannerDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = db.entries()

    fun observeWeek(start: LocalDate): Flow<List<DaySummary>> {
        val monday = CalendarRules.weekStart(start)
        return dao.observeCounts(monday, monday.plusDays(6)).map {
            db.withTransaction {
                val counts = dao.counts(monday, monday.plusDays(6)).associate { it.plannedDate to it.count }
                (0L..6L).map { offset ->
                    val date = monday.plusDays(offset)
                    DaySummary(date, counts[date] ?: 0, dao.first(date))
                }
            }
        }
    }

    fun observeDay(date: LocalDate): Flow<List<Entry>> = dao.observeDay(date)

    fun observeWeekEntries(monday: LocalDate): Flow<List<Entry>> =
        dao.observeWeekEntries(monday, monday.plusDays(6))
    fun observeMarkedDates(start: LocalDate, end: LocalDate): Flow<List<LocalDate>> = dao.observeMarkedDates(start, end)
    suspend fun get(id: String): Entry? = dao.get(id)

    suspend fun save(entry: Entry) {
        val cleaned = entry.copy(title = EntryRules.cleanTitle(entry.title))
        require(EntryRules.errors(cleaned).isEmpty()) { EntryRules.errors(cleaned).values.joinToString(". ") }
        db.withTransaction {
            val existing = dao.get(cleaned.id)
            dao.upsert(cleaned.copy(
                createdAtUtc = existing?.createdAtUtc ?: cleaned.createdAtUtc,
                updatedAtUtc = Instant.now(clock),
                searchTitle = SearchRules.normalize(cleaned.title),
                searchBody = SearchRules.normalize(cleaned.body),
            ))
        }
    }

    suspend fun setCompleted(id: String, completed: Boolean) = dao.setCompleted(id, completed, Instant.now(clock))
    suspend fun toggleCompleted(id: String) = db.withTransaction {
        dao.get(id)?.let { dao.setCompleted(id, !it.completed, Instant.now(clock)) }
        Unit
    }
    suspend fun delete(id: String) = dao.delete(id)

    fun observeSearch(query: String, limit: Int = 50): Flow<List<Entry>> {
        val words = SearchRules.words(query).distinct()
        val order = " ORDER BY plannedDate DESC, localTime IS NULL, localTime, createdAtUtc, id LIMIT ?"
        val pageSize = limit.coerceAtLeast(1)
        // Older Android SQLite builds have small limits on bind parameters and expression depth.
        if (words.size > 200) {
            val sql = """
                WITH RECURSIVE terms(word, rest) AS (
                    SELECT '', ? || ' '
                    UNION ALL
                    SELECT substr(rest, 1, instr(rest, ' ') - 1), substr(rest, instr(rest, ' ') + 1)
                    FROM terms WHERE rest <> ''
                )
                SELECT * FROM entries WHERE NOT EXISTS (
                    SELECT 1 FROM terms WHERE word <> ''
                    AND instr(searchTitle, word) = 0 AND instr(searchBody, word) = 0
                )
            """.trimIndent() + order
            return dao.observeSearch(SimpleSQLiteQuery(sql, arrayOf<Any>(words.joinToString(" "), pageSize)))
        }
        val conditions = words.joinToString(" AND ") { "(instr(searchTitle, ?) > 0 OR instr(searchBody, ?) > 0)" }
        val args: List<Any> = words.flatMap { listOf(it, it) } + pageSize
        val sql = "SELECT * FROM entries WHERE ${conditions.ifEmpty { "0" }}" + order
        return dao.observeSearch(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }
}
