package ru.workweek.planner

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.workweek.planner.data.*

/** Captures the production Activity, including its Room queries and DataStore styling. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w360dp-h640dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val add = hasContentDescription("Добавить запись на", substring = true)

    @Before fun resetHostFixture() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PlannerDatabase.open(context).clearAllTables()
        }
        SettingsRepository(context).resetAppearance()
    }

    private fun waitForWeek() {
        compose.waitUntil(15000) {
            compose.onAllNodes(add).fetchSemanticsNodes().size == 7 &&
                compose.onAllNodesWithText("Загрузка…").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun sevenVisible() {
        waitForWeek()
        val nodes = compose.onAllNodes(add).fetchSemanticsNodes()
        assertEquals(7, nodes.size)
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        nodes.forEach {
            val bounds = it.boundsInRoot
            assertTrue("Добавление должно быть полностью доступно: $bounds", bounds.height >= 48 && bounds.top >= 0 && bounds.bottom <= bottom)
        }
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = if (dialog) requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog().window).decorView
                else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/reports/visual").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForSurface(theme: Theme) {
        val expected = if (theme == Theme.LIGHT) 0xfff6f7fb.toInt() else 0xff10151f.toInt()
        compose.waitUntil(10000) {
            var matches = false
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                matches = bitmap.getPixel(0, 0) == expected
            }
            matches
        }
    }

    private fun captureBothThemes(label: String, dialog: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        for (theme in listOf(Theme.LIGHT, Theme.DARK)) {
            runBlocking { settings.updateAppearance(AppearanceSettings(theme = theme)) }
            waitForSurface(theme)
            capture("$label-${theme.name.lowercase()}", dialog)
        }
    }

    @Test fun systemThemeFollowsConfigurationAndExplicitSelectionSurvivesChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        fun configureNight(night: Boolean) {
            org.robolectric.RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
            compose.activityRule.scenario.recreate()
            waitForWeek()
        }
        try {
            configureNight(false)
            waitForSurface(Theme.LIGHT)
            configureNight(true)
            waitForSurface(Theme.DARK)
            runBlocking { settings.updateAppearance(AppearanceSettings(theme = Theme.LIGHT)) }
            waitForSurface(Theme.LIGHT)
            configureNight(false)
            configureNight(true)
            waitForSurface(Theme.LIGHT)
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")
        }
    }

    @Test fun readLongEntriesAndInspectScreensInBothThemes() {
        waitForWeek()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EntryRepository(PlannerDatabase.open(context))
        val date = LocalDate.now()
        val longTitle = "Подготовить подробный отчёт к встрече ".repeat(4).trim()
        val body = "Сверить таблицу и согласовать сроки.\n".repeat(230) + "Последняя строка 📅"
        runBlocking {
            repeat(20) { i ->
                repo.save(Entry(id = "day-$i", title = if (i == 19) longTitle else "Рабочая встреча №$i",
                    body = if (i == 19) body else "", plannedDate = date, localTime = LocalTime.of(9, i)))
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Ещё 19", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ещё 19", substring = true).performClick()
        compose.onNodeWithText("Добавить запись").assertExists()
        captureBothThemes("day")
        val dayList = compose.onNode(hasScrollToIndexAction())
        dayList.performScrollToIndex(19)
        val description = compose.onNodeWithText(body, useUnmergedTree = true)
        description.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        description.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(body.length, layout.getLineEnd(layout.lineCount - 1))
        capture("day-long-entry")
        dayList.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, layout.size.height.toFloat()) }
        description.assertIsDisplayed()
        assertTrue(description.getUnclippedBoundsInRoot().bottom <= dayList.getUnclippedBoundsInRoot().bottom)
        capture("day-last-lines")
        dayList.performScrollToIndex(19)
        compose.onNodeWithText(longTitle).assertIsDisplayed().performClick()
        compose.onNodeWithText(longTitle).assertExists()
        compose.onNodeWithText(body).assertExists()
        captureBothThemes("editor")
        compose.onNodeWithText("Дата ·", substring = true).performScrollTo().assertIsDisplayed()
        capture("editor-last-lines")
        compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
        compose.onNodeWithText("Удалить запись?").assertIsDisplayed()
        captureBothThemes("delete-dialog", dialog = true)
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Название").performScrollTo().performTextReplacement("Исправленный заголовок")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Продолжить редактирование").assertIsDisplayed()
        captureBothThemes("unsaved-dialog", dialog = true)
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithText("Название").performTextReplacement(longTitle)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        captureBothThemes("calendar")
        val monthName = date.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.forLanguageTag("ru"))
        compose.onNodeWithText("$monthName ${date.year}").performClick()
        compose.onNodeWithText("Месяц и год").assertIsDisplayed()
        captureBothThemes("month-picker")
        compose.onNodeWithText("Месяц, 1–12").performTextReplacement("13")
        compose.onNodeWithText("Показать").assertIsNotEnabled()
        compose.onNodeWithText("Месяц, 1–12").performTextReplacement("2")
        compose.onNodeWithText("Год, 1900–2100").performTextReplacement("2028")
        compose.onNodeWithText("Показать").performClick()
        compose.onNodeWithText("февраль 2028").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("совпаденийнет")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Ничего не найдено").fetchSemanticsNodes().isNotEmpty() }
        captureBothThemes("search-empty")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Открыть настройки").performClick()
        captureBothThemes("settings")
    }

    @Test fun appearanceChoicesSurviveRecreationAndResetKeepsEntryAndSchedule() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        val repo = EntryRepository(PlannerDatabase.open(context))
        val entry = Entry(title = "Ёж и отчёт 📅", plannedDate = LocalDate.now())
        val schedule = runBlocking {
            repo.save(entry)
            settings.updateReminder(false, LocalTime.of(18, 45))
            settings.reminderSettings()
        }
        compose.onNodeWithContentDescription("Открыть настройки").performClick()
        fun choose(label: String) = compose.onNodeWithText(label).performScrollTo().performClick()
        fun checkAppearance(check: (AppearanceSettings) -> Boolean) {
            compose.waitUntil(10000) { check(runBlocking { settings.settings.first().appearance }) }
        }
        choose("С засечками")
        checkAppearance { it.fontFamily == NoteFontFamily.SERIF }
        choose("Без засечек")
        checkAppearance { it.fontFamily == NoteFontFamily.SANS }
        for (size in listOf(14, 16, 18)) {
            choose("$size sp")
            checkAppearance { it.fontSizeSp == size }
        }
        choose("Внутри карточки")
        checkAppearance { it.datePlacement == DatePlacement.INSIDE }
        choose("Янтарный")
        checkAppearance { it.accent == Accent.AMBER }
        choose("Тёмная")
        checkAppearance { it.theme == Theme.DARK }
        compose.activityRule.scenario.recreate()
        checkAppearance {
            it.theme == Theme.DARK && it.accent == Accent.AMBER && it.fontSizeSp == 18 &&
                it.datePlacement == DatePlacement.INSIDE && it.fontFamily == NoteFontFamily.SANS
        }
        compose.onNodeWithText("Сбросить оформление").performScrollTo().performClick()
        checkAppearance { it == AppearanceSettings() }
        runBlocking {
            assertEquals(schedule, settings.reminderSettings())
            assertEquals(entry.title, repo.get(entry.id)?.title)
            assertEquals(entry.plannedDate, repo.get(entry.id)?.plannedDate)
        }
    }

    @Test @Config(qualifiers = "ru-w360dp-h568dp-port-mdpi")
    fun sevenCardsWithReservedSystemBarHeight() {
        sevenVisible()
        capture("week-568-content")
    }

    @Test @Config(fontScale = 1.5f)
    fun smallPhoneAt150Percent() = captureBothThemes("360-150")

    @Test @Config(qualifiers = "ru-w412dp-h915dp-port-mdpi")
    fun tallPhoneAt100Percent() = captureBothThemes("412-100")

    @Test @Config(qualifiers = "ru-w412dp-h915dp-port-mdpi", fontScale = 2f)
    fun tallPhoneAt200Percent() = captureBothThemes("412-200")

    @Test @Config(fontScale = 2.0f)
    fun largeTextWeekCanReachSundayAndCreateAnEntry() {
        captureBothThemes("360-200")
        capture("week-200")
        val sunday = compose.onAllNodes(add)[6]
        sunday.performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Сохранить").performScrollTo().assertIsDisplayed()
        capture("editor-200")
    }

    @Test @Config(qualifiers = "ru-w412dp-h915dp-port-night-mdpi", fontScale = 1.5f)
    fun darkTallPhoneWithLargeText() {
        captureBothThemes("412-150")
        capture("week-dark-150")
        compose.onAllNodes(add)[6].performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "ru-w640dp-h360dp-land-mdpi", fontScale = 2f)
    fun landscapeStillReachesLastDay() {
        waitForWeek()
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        compose.onNodeWithText("Ввести дату вручную").performScrollTo().performClick()
        compose.onNodeWithText("ДД.ММ.ГГГГ").performTextReplacement("31.12.2026")
        compose.onNodeWithText("Перейти").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals("28.12.2026 — 03.01.2027")
        compose.onAllNodes(add)[6].performScrollTo().assertIsDisplayed()
        capture("week-landscape-200")
    }

    @Test fun emptyAndPopulatedWeekInBothThemesAndRelatedScreens() {
        sevenVisible()
        capture("week-empty")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = EntryRepository(PlannerDatabase.open(context))
        val settings = SettingsRepository(context)
        val friday = LocalDate.now().with(java.time.DayOfWeek.FRIDAY)
        val appearance = AppearanceSettings(theme = Theme.LIGHT, datePlacement = DatePlacement.INSIDE)
        runBlocking {
            settings.updateAppearance(appearance)
            repeat(20) { index ->
                repo.save(Entry(title = if (index == 0) "Согласовать отчёт с клиентом" else "Рабочая встреча №$index",
                    body = "Сверить итоги перед отправкой.", plannedDate = friday, completed = true,
                    localTime = LocalTime.of(9, index)))
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Ещё 19", substring = true).fetchSemanticsNodes().isNotEmpty() }
        sevenVisible()
        capture("week-light")
        runBlocking { settings.updateAppearance(appearance.copy(theme = Theme.DARK)) }
        compose.waitForIdle()
        capture("week-dark")
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ОТЧЕТ")
        compose.waitUntil(15000) { compose.onAllNodesWithText("Согласовать отчёт с клиентом").fetchSemanticsNodes().isNotEmpty() }
        capture("search")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        compose.onNodeWithText("Выбор даты").assertExists()
        capture("calendar")
    }
}
