package ru.workweek.planner.notifications

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.workweek.planner.data.DigestDelivery
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.ReminderSettings

class DigestEngineTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val moscow = ZoneId.of("Europe/Moscow")

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
    }

    private class Store : DigestStore {
        var config = ReminderSettings(true, LocalTime.of(20, 0), 1)
        val records = mutableMapOf<LocalDate, DigestDelivery>()
        var notes = emptyList<Entry>()
        override suspend fun settings() = config
        override suspend fun update(enabled: Boolean, time: LocalTime) {
            if (enabled != config.enabled || time != config.localDeliveryTime) {
                config = ReminderSettings(enabled, time, config.scheduleRevision + 1)
            }
        }
        override suspend fun bumpRevision() { config = config.copy(scheduleRevision = config.scheduleRevision + 1) }
        override suspend fun entries(date: LocalDate) = notes
        override suspend fun delivery(date: LocalDate) = records[date]
        override suspend fun record(delivery: DigestDelivery) { records[delivery.sendLocalDate] = delivery }
    }

    private class Platform : DigestPlatform {
        var available = true
        var failPublish = false
        var alarm: DigestAlarm? = null
        val published = mutableListOf<Pair<LocalDate, List<Entry>>>()
        override fun notificationsAvailable() = available
        override fun schedule(alarm: DigestAlarm) { this.alarm = alarm }
        override fun cancel() { alarm = null }
        override fun publish(sendDate: LocalDate, targetDate: LocalDate, entries: List<Entry>) {
            if (failPublish) error("Injected failure")
            published += targetDate to entries
        }
    }

    private fun entry(title: String = "Отчёт") = Entry(title = title, plannedDate = day.plusDays(1))
    private fun alarm(date: LocalDate = day, revision: Long = 1, time: LocalTime = LocalTime.of(20, 0)) =
        DigestAlarm(date, date.plusDays(1), revision, time, DigestEngine.moment(date, time, moscow))

    @Test fun dueCallbackReadsFreshDataAndPublishesOnceAcrossConcurrentCallbacksAndRestart() = runBlocking {
        val store = Store().apply { notes = listOf(entry("Актуальный отчёт")) }
        val platform = Platform()
        val clock = MutableClock(Instant.parse("2026-11-20T17:01:00Z"))
        val engine = DigestEngine(store, platform, clock) { moscow }
        coroutineScope { List(8) { async { engine.callback(alarm()) } }.forEach { it.await() } }
        DigestEngine(store, platform, clock) { moscow }.refresh()
        assertEquals(1, platform.published.size)
        assertEquals("Актуальный отчёт", platform.published.single().second.single().title)
        assertEquals("PUBLISHED", store.records[day]?.deliveryState)
        assertEquals(day.plusDays(1), platform.alarm?.sendDate)
    }

    @Test fun staleRevisionDoesNotPublishOrReplaceNewSchedule() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform()
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T17:05:00Z"))) { moscow }
        engine.update(true, LocalTime.of(22, 0))
        val latest = platform.alarm
        engine.callback(alarm())
        assertEquals(latest, platform.alarm)
        assertTrue(platform.published.isEmpty())
    }

    @Test fun earlyCallbackAndClockMovedBackCannotPublish() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform()
        val clock = MutableClock(Instant.parse("2026-11-20T16:59:00Z"))
        val engine = DigestEngine(store, platform, clock) { moscow }
        engine.callback(alarm())
        assertTrue(platform.published.isEmpty())
        assertEquals(day, platform.alarm?.sendDate)
        engine.refresh(systemTimeChanged = true)
        engine.callback(alarm())
        assertEquals(2L, platform.alarm?.revision)
        assertTrue(platform.published.isEmpty())
    }

    @Test fun previousDayCallbackAtMidnightSkipsOldDigestAndSchedulesFuture() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform()
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T21:05:00Z"))) { moscow }
        engine.callback(alarm())
        assertTrue(platform.published.isEmpty())
        assertEquals(day.plusDays(1), platform.alarm?.sendDate)
        assertEquals(day.plusDays(2), platform.alarm?.targetDate)
    }

    @Test fun emptyOrDeniedIsNotSuccessAndLaterRefreshCatchesUpOnce() = runBlocking {
        val store = Store()
        val platform = Platform()
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T18:00:00Z"))) { moscow }
        engine.refresh()
        assertTrue(store.records.isEmpty())
        store.notes = listOf(entry())
        platform.available = false
        engine.refresh()
        assertTrue(store.records.isEmpty())
        platform.available = true
        engine.refresh()
        store.notes = listOf(entry("После отправки"))
        engine.refresh()
        assertEquals(1, platform.published.size)
    }

    @Test fun disabledCancelsAndOldCallbackCannotRestoreIt() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform()
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T16:00:00Z"))) { moscow }
        engine.refresh()
        engine.update(false, LocalTime.of(20, 0))
        engine.callback(alarm())
        assertNull(platform.alarm)
        assertTrue(platform.published.isEmpty())
    }

    @Test fun failedPublishKeepsAttemptAndFutureScheduleThenRetriesSameKey() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform().apply { failPublish = true }
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T18:00:00Z"))) { moscow }
        assertTrue(runCatching { engine.refresh() }.isFailure)
        assertEquals("ATTEMPTING", store.records[day]?.deliveryState)
        assertNotNull(platform.alarm)
        platform.failPublish = false
        engine.refresh()
        assertEquals("digest-2026-11-20", store.records[day]?.notificationKey)
        assertEquals(1, platform.published.size)
    }

    @Test fun dstGapAndOverlapUseCalendarRules() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"),
            DigestEngine.moment(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), berlin))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"),
            DigestEngine.moment(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), berlin))
    }

    @Test fun timezoneChangeRecalculatesLocalTimeAndSuppressesSameDateDuplicate() = runBlocking {
        val store = Store().apply { notes = listOf(entry()) }
        val platform = Platform()
        var zone = moscow
        val engine = DigestEngine(store, platform, MutableClock(Instant.parse("2026-11-20T18:00:00Z"))) { zone }
        engine.refresh()
        zone = ZoneId.of("Asia/Dubai")
        engine.refresh(true)
        assertEquals(1, platform.published.size)
        assertEquals(Instant.parse("2026-11-21T16:00:00Z"), platform.alarm?.triggerAt)
        assertEquals(day.plusDays(1), store.notes.single().plannedDate)
    }

    @Test fun russianFormsAndPreviewAreBounded() {
        assertEquals("Завтра у вас 1 дело", DigestEngine.title(1))
        assertEquals("Завтра у вас 2 дела", DigestEngine.title(2))
        assertEquals("Завтра у вас 5 дел", DigestEngine.title(5))
        assertEquals("Завтра у вас 11 дел", DigestEngine.title(11))
        val notes = List(7) { entry("Запись $it") }
        assertEquals(listOf("Запись 0", "Запись 1", "Запись 2", "И ещё 4"), DigestEngine.lines(notes))
    }
}
