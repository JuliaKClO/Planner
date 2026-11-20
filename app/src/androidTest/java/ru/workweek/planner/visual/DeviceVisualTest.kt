package ru.workweek.planner.visual

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.workweek.planner.MainActivity
import ru.workweek.planner.data.*

@RunWith(AndroidJUnit4::class)
class DeviceVisualTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("evidence"))
        File(folder, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    @Test fun captureWeekCalendarAndRussianSearch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val repo = EntryRepository(PlannerDatabase.open(context))
        val settings = SettingsRepository(context)
        val date = LocalDate.of(2028, 11, 24)
        val appearance = AppearanceSettings(theme = Theme.LIGHT, datePlacement = DatePlacement.INSIDE)
        runBlocking { settings.updateAppearance(appearance) }
        ui.onNodeWithContentDescription("Выбрать дату").performClick()
        ui.onNodeWithText("Ввести дату вручную").performClick()
        ui.onNodeWithText("ДД.ММ.ГГГГ").performTextReplacement("24.11.2028")
        ui.onNodeWithText("Перейти").performScrollTo().performClick()
        val plus = hasContentDescription("Добавить запись на", substring = true)
        ui.waitUntil(15000) { ui.onAllNodesWithText("Нет записей").fetchSemanticsNodes().size == 7 }
        assertEquals(7, ui.onAllNodes(plus).fetchSemanticsNodes().size)
        capture("week-empty")
        try {
            runBlocking {
                repeat(20) { i ->
                    repo.save(Entry(id = "visual-friday-$i", title = if (i == 0) "Согласовать отчёт с клиентом" else "Дело №$i",
                        body = "Сверить сроки и отправить итоговый документ.", plannedDate = date, localTime = LocalTime.of(9, i), completed = true))
                }
                listOf("Планирование недели", "Обсудить смету", "Подготовить презентацию").forEachIndexed { i, title ->
                    repo.save(Entry(id = "visual-week-$i", title = title, plannedDate = date.minusDays(4L - i), localTime = LocalTime.of(10 + i, 30)))
                }
            }
            ui.waitUntil(15000) { ui.onAllNodesWithText("Ещё 19", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("week-light")
            runBlocking { settings.updateAppearance(appearance.copy(theme = Theme.DARK)) }
            capture("week-dark")
            ui.onNodeWithContentDescription("Выбрать дату").performClick()
            ui.onNodeWithText("Выбор даты").assertExists()
            capture("calendar")
            ui.onNodeWithText("Отмена").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Открыть поиск").performClick()
            ui.onNode(hasSetTextAction()).performTextInput("ОТЧЕТ")
            ui.waitUntil(15000) {
                ui.onAllNodesWithText("Согласовать отчёт с клиентом").fetchSemanticsNodes().isNotEmpty()
            }
            capture("search")
        } finally {
            runBlocking {
                repeat(20) { repo.delete("visual-friday-$it") }
                repeat(3) { repo.delete("visual-week-$it") }
                settings.resetAppearance()
            }
        }
    }
}
