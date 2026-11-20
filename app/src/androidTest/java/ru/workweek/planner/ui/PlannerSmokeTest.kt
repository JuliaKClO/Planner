package ru.workweek.planner.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import ru.workweek.planner.MainActivity
import ru.workweek.planner.data.EntryRepository
import ru.workweek.planner.data.PlannerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import org.junit.Assert.assertEquals

/** Run only with -PisolatedTest so smoke data never enters the personal installation. */
@RunWith(AndroidJUnit4::class)
class PlannerSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun requireIsolatedInstallation() {
        check(android.os.Build.FINGERPRINT == "robolectric" || compose.activity.packageName.endsWith(".qa")) {
            "Use -PisolatedTest for device UI tests"
        }
        waitForWeek()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForWeek() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Открыть поиск").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun enterDate(date: String) {
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        compose.onNodeWithText("Ввести дату вручную").performClick()
        compose.onNodeWithText("ДД.ММ.ГГГГ").performTextReplacement(date)
    }

    private fun newEntry(title: String) {
        compose.onAllNodes(hasContentDescription("Добавить запись на", substring = true))[0].performClick()
        compose.onNodeWithText("Название").performTextInput(title)
    }

    private fun saveEntry() {
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForWeek()
    }

    @Test fun sevenDaysCreateSearchAndRecreate() {
        val week = compose.onNode(hasScrollToIndexAction())
        val monday = startOfWeek(LocalDate.now())
        for (offset in 0L..6L) week.performScrollToKey(monday.plusDays(offset).toEpochDay())
        week.performScrollToKey(monday.toEpochDay())
        compose.onAllNodes(hasContentDescription("Добавить запись на", substring = true))[0].performClick()
        val title = "ОТЧЁТ клиент ${System.nanoTime()}"
        compose.onNodeWithText("Название").performTextInput(title)
        compose.onNodeWithText("Описание").performTextInput("Договор и emoji 📅")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Открыть поиск").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNodeWithText("Название или описание").performTextInput(title.lowercase().replace('ё', 'е'))
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        compose.onNodeWithText("Название").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Название").assertExists()
        compose.onNodeWithText(title).assertExists()
    }

    @Test fun invalidCalendarDateRemainsEditable() {
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        compose.onNodeWithText("Ввести дату вручную").performClick()
        compose.onNodeWithText("ДД.ММ.ГГГГ").performTextReplacement("31.02.2027")
        compose.onNodeWithText("Перейти").performScrollTo().performClick()
        compose.onNodeWithText("31.02.2027").assertExists()
        compose.onNodeWithText("Введите существующую дату с 01.01.1900 по 31.12.2100").assertExists()
        compose.onNodeWithText("ДД.ММ.ГГГГ").performTextReplacement("29.02.2028")
        compose.onNodeWithText("Перейти").performScrollTo().performClick()
        compose.onNode(hasContentDescription("вторник, 29 февраля 2028, выбрано", substring = true)).assertExists()
    }

    @Test fun calendarChoiceKeepsCivilDateInBothTimeZonesAndStopsAtLimits() {
        val original = java.util.TimeZone.getDefault()
        try {
            for (id in listOf("GMT-08:00", "GMT+14:00")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(id))
                enterDate("29.02.2028")
                compose.onNodeWithText("Перейти").performScrollTo().performClick()
                compose.onNode(hasContentDescription("вторник, 29 февраля 2028, выбрано", substring = true)).assertExists()
            }
            enterDate("01.01.1900")
            compose.onNodeWithText("Перейти").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Предыдущая неделя").assertIsNotEnabled()
            enterDate("26.12.2100")
            compose.onNodeWithText("Перейти").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Следующая неделя").performClick()
            compose.onNode(hasContentDescription("пятница, 31 декабря 2100, выбрано", substring = true)).assertExists()
            compose.onNodeWithContentDescription("Следующая неделя").assertIsNotEnabled()
            val sunday = hasContentDescription("Добавить запись на воскресенье", substring = true)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(sunday)
            compose.onNode(sunday).assertIsNotEnabled()
        } finally { java.util.TimeZone.setDefault(original) }
    }

    @Test fun calendarNavigationConfirmationAndCancellation() {
        enterDate("31.12.2026")
        compose.onNodeWithText("Перейти").performScrollTo().performClick()
        val december = weekLabel(LocalDate.of(2026, 12, 28))
        val january = weekLabel(LocalDate.of(2027, 1, 4))
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(december)
        compose.onNodeWithText("Пн 28").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(january)
        compose.onNodeWithText("Пн 4").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(december)
        repeat(20) { compose.onNodeWithContentDescription("Следующая неделя").performClick() }
        repeat(20) { compose.onNodeWithContentDescription("Предыдущая неделя").performClick() }
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(december)
        enterDate("29.02.2028")
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(december)
        repeat(100) { compose.onNodeWithContentDescription("Следующая неделя").performClick() }
        compose.onNodeWithText("Сегодня").performClick()
        val monday = startOfWeek(LocalDate.now())
        compose.onNodeWithContentDescription("Выбрать дату").assertTextEquals(weekLabel(monday))
    }

    @Test fun draftSurvivesRecreationAndAutosavesOnBack() {
        val title = "Черновик ${System.nanoTime()}"
        newEntry(title)
        compose.onNodeWithText("Описание").performTextInput("Несохранённые данные 📅")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText("Несохранённые данные 📅").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForWeek()
        compose.onNodeWithText("Не удалось сохранить запись").assertDoesNotExist()
        val rows = runBlocking { EntryRepository(PlannerDatabase.open(compose.activity)).observeSearch(title).first() }
        assertEquals(1, rows.size)
        assertEquals("Несохранённые данные 📅", rows.single().body)
    }

    @Test fun invalidDraftCanBeCorrectedOrDiscarded() {
        val title = "Невалидный черновик ${System.nanoTime()}"
        newEntry(title)
        compose.onNodeWithText("Время ЧЧ:ММ, необязательно").performScrollTo().performTextInput("25:61")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithText(title).assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Не сохранять").performClick()
        waitForWeek()
        val rows = runBlocking { EntryRepository(PlannerDatabase.open(compose.activity)).observeSearch(title).first() }
        assertEquals(0, rows.size)
    }

    @Test fun editedEntryAutosavesOnCloseAndShowWeek() {
        val title = "Автосохранение ${System.nanoTime()}"
        newEntry(title)
        compose.onNodeWithText("Закрыть").performScrollTo().performClick()
        waitForWeek()
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNodeWithText("Название или описание").performTextInput(title.lowercase())
        waitForText(title)
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Описание").performTextReplacement("Первое изменение")
        compose.onNodeWithText("Закрыть").performScrollTo().performClick()
        waitForText(title)
        val repository = EntryRepository(PlannerDatabase.open(compose.activity))
        val saved = runBlocking { repository.observeSearch(title).first().single() }
        assertEquals("Первое изменение", saved.body)
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Описание").performTextReplacement("Второе изменение")
        compose.onNodeWithText("Показать в неделе").performScrollTo().performClick()
        waitForWeek()
        compose.onNodeWithText("Не удалось сохранить запись").assertDoesNotExist()
        assertEquals("Второе изменение", runBlocking { repository.get(saved.id) }?.body)
    }

    @Test fun databaseFailurePreservesDraftAndRetryIsIdempotent() {
        val title = "Ошибка базы ${System.nanoTime()}"
        val database = PlannerDatabase.open(compose.activity)
        newEntry(title)
        runBlocking { withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER qa_save_failure BEFORE INSERT ON entries BEGIN SELECT RAISE(FAIL, 'controlled QA failure'); END")
        } }
        try {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitForText("Не удалось сохранить запись. Данные остались в форме, повторите попытку.")
            compose.onNodeWithText(title).assertExists()
        } finally {
            runBlocking { withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS qa_save_failure") } }
        }
        val save = compose.onNodeWithText("Сохранить").performScrollTo().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnIdle { save(); save() }
        waitForWeek()
        val rows = runBlocking { EntryRepository(database).observeSearch(title).first() }
        assertEquals(1, rows.size)
    }

    @Test fun latestSearchSurvivesEditAndDeletion() {
        val token = "Поиск${System.nanoTime()}"
        newEntry(token)
        saveEntry()
        compose.onNodeWithContentDescription("Открыть поиск").performClick()
        compose.onNodeWithText("Название или описание").performTextInput("НесуществующийПервыйЗапрос")
        compose.onNodeWithText("Название или описание").performTextReplacement(token.lowercase())
        waitForText(token)
        compose.onNodeWithText(token).performClick()
        compose.onNodeWithText("Название").performTextReplacement("$token изменено")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitForText("$token изменено")
        compose.onNodeWithText(token.lowercase()).assertExists()
        compose.onNodeWithText("$token изменено").performClick()
        compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Название").assertExists()
        compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
        compose.onNodeWithText("Удалить", useUnmergedTree = true).performClick()
        waitForText("Ничего не найдено")
        compose.onNodeWithText(token.lowercase()).assertExists()
    }
}
