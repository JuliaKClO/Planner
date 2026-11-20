package ru.workweek.planner.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.workweek.planner.data.*

/** Exercises the real NotificationManager with a catch-up delivery in the isolated QA install. */
@RunWith(AndroidJUnit4::class)
class SystemDigestTest {
    @Test fun postsOneSystemNotificationUsingEditedTitle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        check(app.packageName.endsWith(".qa"))
        val now = LocalTime.now()
        assumeTrue("Повторите после 00:02", now.isAfter(LocalTime.of(0, 2)))
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val system = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val db = PlannerDatabase.open(app)
        val repo = EntryRepository(db)
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val entry = Entry(title = "Первоначальная версия", plannedDate = tomorrow, localTime = LocalTime.MIDNIGHT)
        val tag = DigestEngine.notificationKey(today)
        val time = now.minusMinutes(1).withSecond(0).withNano(0)
        runBlocking {
            // The QA journal must start clean even if the operator repeats this test today.
            ReminderController.update(app, false, time)
            db.openHelper.writableDatabase.delete("digest_deliveries", "sendLocalDate = ?", arrayOf(today.toEpochDay().toString()))
            system.cancel(tag, 1)
            try {
                repo.save(entry)
                repo.save(entry.copy(title = "Актуальный отчёт"))
                ReminderController.update(app, true, time)
                val published = db.deliveries().get(today)
                assertEquals("PUBLISHED", published?.deliveryState)
                ReminderController.refresh(app)
                assertEquals(published, db.deliveries().get(today))
                val matches = system.activeNotifications.filter { it.tag == tag }
                assertEquals(1, matches.size)
                val notification = matches.single().notification
                val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
                assertTrue(lines.any { it.contains("Актуальный отчёт") })
                assertNotNull(notification.contentIntent)
            } finally {
                ReminderController.update(app, false, time)
                repo.delete(entry.id)
                system.cancel(tag, 1)
            }
        }
    }
}
