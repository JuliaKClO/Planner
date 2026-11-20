package ru.workweek.planner.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.workweek.planner.data.*

@RunWith(RobolectricTestRunner::class)
class MidnightTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dateChangeUpdatesTodayWithoutNavigatingTheOpenWeek() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val date = AtomicReference(LocalDate.of(2026, 11, 22))
        val repo = EntryRepository(PlannerDatabase.open(app))
        val prefs = SettingsRepository(app)
        ui.setContent { PlannerApp(repo, prefs, deviceDate = date::get) }
        ui.onNode(hasContentDescription("воскресенье, 22 ноября 2026, сегодня, выбрано", substring = true)).assertExists()
        date.set(LocalDate.of(2026, 11, 23))
        app.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        ui.waitUntil(10000) {
            ui.onAllNodes(hasContentDescription("воскресенье, 22 ноября 2026, выбрано", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("Выбрать дату").assertTextEquals(weekLabel(LocalDate.of(2026, 11, 16)))
        ui.onNodeWithText("Сегодня").performClick()
        ui.onNode(hasContentDescription("понедельник, 23 ноября 2026, сегодня, выбрано", substring = true)).assertExists()
    }
}
