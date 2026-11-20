package ru.workweek.planner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {
    @Test fun persistenceAndUnicodeSearchOnAndroidSQLite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "instrumented-${System.nanoTime()}.db"
        val date = LocalDate.of(2026, 11, 21)
        val entry = Entry(title = "Отправить отчёт 📚", body = "Клиент 100% _ *", plannedDate = date, localTime = LocalTime.of(9, 0))
        var db = Room.databaseBuilder(context, PlannerDatabase::class.java, name).build()
        try {
            EntryRepository(db).save(entry)
            db.close()
            db = Room.databaseBuilder(context, PlannerDatabase::class.java, name).build()
            val repository = EntryRepository(db)
            assertEquals(entry.id, repository.get(entry.id)!!.id)
            assertEquals(entry.plannedDate, repository.get(entry.id)!!.plannedDate)
            assertEquals(entry.body, repository.get(entry.id)!!.body)
            listOf("ОТЧЁТ", "отчет клиент", "%", "_", "*", "📚").forEach {
                assertEquals(listOf(entry.id), repository.observeSearch(it).first().map { it.id })
            }
            repository.setCompleted(entry.id, true)
            assertTrue(repository.get(entry.id)!!.completed)
            assertTrue(db.entries().digest(date).isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
