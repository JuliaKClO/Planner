package ru.workweek.planner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RepositoryIntegrationTest {
    private lateinit var db: PlannerDatabase
    private lateinit var repo: EntryRepository
    private val date = LocalDate.of(2026, 11, 21)
    private val clock = Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PlannerDatabase::class.java).build()
        repo = EntryRepository(db, clock)
    }
    @After fun close() = db.close()

    @Test fun longLiteralQueryRespectsAndroidSqliteParameterLimit() = runBlocking {
        val query = (1..600).joinToString(" ") { "слово$it/" }
        val match = Entry(title = "Длинный запрос", body = query, plannedDate = date)
        repo.save(match)
        repo.save(match.copy(id = "incomplete", body = query.substringBeforeLast(' ')))
        assertEquals(listOf(match.id), repo.observeSearch(query).first().map { it.id })
    }

    @Test fun DATA_01_03_07_idempotentSaveMoveAndFailedValidation() = runBlocking {
        val original = Entry(title = "  Отчёт\nклиенту  ", body = "Строка 1\nСтрока 2 📚", plannedDate = date)
        repo.save(original)
        repo.save(original)
        assertEquals(1, repo.observeDay(date).first().size)
        val saved = repo.get(original.id)!!
        assertEquals("Отчёт клиенту", saved.title)
        assertEquals(original.body, saved.body)
        repo.save(saved.copy(title = "Новое дело", plannedDate = date.plusDays(1), localTime = LocalTime.of(9, 0)))
        val moved = repo.get(original.id)!!
        assertEquals(saved.id, moved.id)
        assertEquals(saved.createdAtUtc, moved.createdAtUtc)
        assertTrue(repo.observeDay(date).first().isEmpty())
        assertEquals(1, repo.observeDay(date.plusDays(1)).first().size)
        assertTrue(runCatching { repo.save(moved.copy(title = "", body = " ")) }.isFailure)
        assertEquals("Новое дело", repo.get(original.id)!!.title)
    }

    @Test fun DATA_04_05_weekPreviewOrderCountsCompletionAndDelete() = runBlocking {
        val untimed = Entry(title = "Без времени", plannedDate = date)
        val timed = Entry(title = "С утра", plannedDate = date, localTime = LocalTime.of(8, 0))
        val completed = Entry(title = "Готово", plannedDate = date, localTime = LocalTime.of(7, 0), completed = true)
        listOf(untimed, completed, timed).forEach { repo.save(it) }
        assertEquals(listOf(timed.id, untimed.id, completed.id), repo.observeDay(date).first().map { it.id })
        val week = repo.observeWeek(date).first()
        assertEquals(7, week.size)
        assertEquals(3, week.single { it.date == date }.count)
        assertEquals(timed.id, week.single { it.date == date }.first!!.id)
        repo.setCompleted(timed.id, true)
        assertEquals(untimed.id, repo.observeDay(date).first().first().id)
        repo.setCompleted(timed.id, false)
        repo.delete(timed.id)
        assertNull(repo.get(timed.id))
        assertEquals(2, repo.observeDay(date).first().size)
    }

    @Test fun SEARCH_01_02_03_literalRussianSearchAndUnlimitedPaging() = runBlocking {
        val old = Entry(title = "Отправить ОТЧЁТ", body = "Клиент % _ ' \" * 📚", plannedDate = date.minusYears(5), completed = true)
        repo.save(old)
        repeat(70) { repo.save(Entry(id = "future-$it", title = "Отчет", body = "клиент", plannedDate = date.plusDays(it.toLong()))) }
        listOf("ОТЧЁТ", "отчет", " отчёт ", "отчет клиент").forEach {
            assertEquals(71, repo.observeSearch(it, 100).first().size)
        }
        assertEquals(50, repo.observeSearch("отчет").first().size)
        listOf("%", "_", "'", "\"", "*", "📚").forEach {
            assertEquals(listOf(old.id), repo.observeSearch(it).first().map { it.id })
        }
        assertTrue(repo.observeSearch("").first().isEmpty())
        assertTrue(repo.observeSearch("несуществующий").first().isEmpty())
        repo.save(old.copy(title = "Изменено", body = "Другое"))
        assertTrue(repo.observeSearch("📚").first().isEmpty())
    }

    @Test fun REM_01_databaseSelectsOnlyCurrentEligibleEntries() = runBlocking {
        val eligible = listOf(
            Entry(title = "Первое", plannedDate = date, localTime = LocalTime.of(9, 0)),
            Entry(title = "Второе", plannedDate = date),
        )
        val excluded = listOf(
            Entry(title = "Выполнено", plannedDate = date, completed = true),
            Entry(title = "Без сводки", plannedDate = date, remindOnPreviousDay = false),
            Entry(title = "Позже", plannedDate = date.plusDays(1)),
        )
        (eligible + excluded).forEach { repo.save(it) }
        assertEquals(eligible.map { it.id }, db.entries().digest(date).map { it.id })
        repo.setCompleted(eligible.first().id, true)
        assertEquals(listOf(eligible.last().id), db.entries().digest(date).map { it.id })
    }

    @Test fun DATA_08_deliveryJournalAndEntriesSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${System.nanoTime()}.db"
        val persistent = Room.databaseBuilder(context, PlannerDatabase::class.java, name).build()
        val entry = Entry(title = "Сохранить 📚", plannedDate = date)
        val delivery = DigestDelivery(date, date.plusDays(1), "digest:$date", "PUBLISHED", Instant.now(clock))
        EntryRepository(persistent, clock).save(entry)
        persistent.deliveries().upsert(delivery)
        persistent.close()
        val reopened = Room.databaseBuilder(context, PlannerDatabase::class.java, name).build()
        try {
            assertEquals(entry.id, reopened.entries().get(entry.id)!!.id)
            assertEquals(entry.title, reopened.entries().get(entry.id)!!.title)
            assertEquals(delivery, reopened.deliveries().get(date))
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun UI_07_settingsResetPreservesReminders() = runBlocking {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings.updateReminder(true, LocalTime.of(19, 30))
        val reminder = settings.reminderSettings()
        settings.updateAppearance(AppearanceSettings(Theme.DARK, NoteFontFamily.SERIF, 18, DatePlacement.INSIDE, Accent.AMBER))
        assertEquals(Theme.DARK, settings.settings.first().appearance.theme)
        settings.resetAppearance()
        assertEquals(AppearanceSettings(), settings.settings.first().appearance)
        assertEquals(reminder, settings.reminderSettings())
        val again = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals(reminder, again.reminderSettings())
        settings.updateReminder(true, LocalTime.of(19, 30))
        assertEquals(reminder.scheduleRevision, settings.reminderSettings().scheduleRevision)
    }
}
