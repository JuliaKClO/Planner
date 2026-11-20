package ru.workweek.planner.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.EntryRepository
import ru.workweek.planner.data.PlannerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDigestPlatformTest {
    private lateinit var context: Context
    private lateinit var platform: AndroidDigestPlatform
    private val day = LocalDate.of(2026, 11, 20)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        platform = AndroidDigestPlatform(context)
    }

    @Test fun registersOneInexactIdleAlarmWithImmutablePayloadAndCancels() {
        val alarm = DigestAlarm(day, day.plusDays(1), 5, LocalTime.of(20, 0),
            Instant.parse("2026-11-20T17:00:00Z"))
        platform.schedule(alarm)
        platform.schedule(alarm.copy(revision = 6))
        val alarms = shadowOf(requireNotNull(context.getSystemService(AlarmManager::class.java))).scheduledAlarms
        assertEquals(1, alarms.size)
        val scheduled = alarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)
        assertEquals(alarm.triggerAt.toEpochMilli(), scheduled.triggerAtTime)
        assertTrue(scheduled.allowWhileIdle)
        assertTrue(scheduled.windowLengthMs != 0L)
        assertEquals(0L, scheduled.interval)
        val payload = shadowOf(requireNotNull(scheduled.operation)).savedIntent
        assertEquals(6L, payload.getLongExtra("revision", -1))
        assertEquals(day.toString(), payload.getStringExtra("sendDate"))
        assertEquals(day.plusDays(1).toString(), payload.getStringExtra("targetDate"))
        platform.cancel()
        assertTrue(shadowOf(requireNotNull(context.getSystemService(AlarmManager::class.java))).scheduledAlarms.isEmpty())
    }

    @Test fun repeatedPublishUsesSameCardAndPreservesNotificationDestination() {
        val notes = listOf(Entry(title = "Отчёт", plannedDate = day.plusDays(1)))
        platform.publish(day, day.plusDays(1), notes)
        platform.publish(day, day.plusDays(1), notes)
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, manager.activeNotifications.size)
        val first = manager.activeNotifications.single().notification
        assertEquals("2026-11-21", shadowOf(first.contentIntent).savedIntent
            .getStringExtra(ReminderController.TARGET_DATE))
        platform.schedule(DigestAlarm(day.plusDays(1), day.plusDays(2), 1,
            LocalTime.of(20, 0), Instant.parse("2026-11-21T17:00:00Z")))
        platform.publish(day.plusDays(1), day.plusDays(2), notes)
        assertEquals("2026-11-21", shadowOf(first.contentIntent).savedIntent
            .getStringExtra(ReminderController.TARGET_DATE))
        assertTrue(first.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun permissionAppAndChannelStatusReflectSystemState() {
        assertTrue(platform.notificationsAvailable())
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(platform.notificationsAvailable())
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(platform.notificationsAvailable())
    }

    @Test fun blockedNotificationChannelIsReportedUnavailable() {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(android.app.NotificationChannel(ReminderController.CHANNEL_ID,
            "Планы", NotificationManager.IMPORTANCE_NONE))
        assertFalse(platform.notificationsAvailable())
    }

    @Test fun realStoreAndAdapterUseEditedEntriesAndPersistOnePublication() = runBlocking {
        val zone = ZoneId.of("Europe/Moscow")
        val clock = Clock.fixed(Instant.parse("2026-11-20T17:09:00Z"), zone)
        val store = RoomDigestStore(context)
        val db = PlannerDatabase.open(context)
        val repo = EntryRepository(db, clock)
        val time = LocalTime.of(20, 0)
        store.update(true, time)
        val engine = DigestEngine(store, platform, clock) { zone }
        val entry = Entry(title = "Первоначальный текст", plannedDate = day.plusDays(1))
        repo.save(entry)
        repo.save(entry.copy(title = "Обновлённый отчёт"))
        val alarm = DigestAlarm(day, day.plusDays(1), store.settings().scheduleRevision, time, DigestEngine.moment(day, time, zone))
        coroutineScope { repeat(6) { launch(Dispatchers.IO) { engine.callback(alarm) } } }
        val journal = db.deliveries().all()
        assertEquals(1, journal.size)
        assertEquals("PUBLISHED", journal.single().deliveryState)
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val first = manager.activeNotifications.single()
        assertEquals("Обновлённый отчёт", first.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        repo.save(entry.copy(title = "После отправки"))
        val restarted = DigestEngine(RoomDigestStore(context), AndroidDigestPlatform(context), clock) { zone }
        restarted.callback(alarm)
        restarted.refresh()
        val current = manager.activeNotifications.single()
        assertEquals(first.postTime, current.postTime)
        assertEquals(first.tag, current.tag)
        assertEquals("Обновлённый отчёт", current.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals(journal, db.deliveries().all())
    }
}
