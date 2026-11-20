package ru.workweek.planner.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.EntryRules

class SearchAndEntryRulesTest {
    @Test fun SEARCH_01_russianNormalizationAndAndMatching() {
        listOf("ОТЧЁТ", "отчет", " отчёт ").forEach {
            assertTrue(SearchRules.matches("Отправить отчёт", "клиенту", it))
        }
        assertTrue(SearchRules.matches("Отправить отчёт", "Клиент сегодня", "клиент отчет"))
        assertFalse(SearchRules.matches("отчёт", "", "отчет клиент"))
        assertTrue(SearchRules.matches("ＡＢＣ", "", "abc"))
        assertTrue(SearchRules.matches("отчёт", "клиент", "отчет\u00A0клиент"))
    }

    @Test fun SEARCH_02_literalMetacharactersAndEmoji() {
        listOf("%", "_", "'", "\"", "*", "📚").forEach {
            assertTrue(SearchRules.matches("Дело $it", "", it))
            assertFalse(SearchRules.matches("Дело", "", it))
        }
    }

    @Test fun SEARCH_03_emptyQueryDoesNotMatchArchive() {
        listOf("", " ", "\u00A0\u2003").forEach {
            assertFalse(SearchRules.matches("отчёт", "клиент", it))
        }
    }

    @Test fun DATA_02_unicodeValidationDoesNotTruncate() {
        val entry = Entry(title = "📚".repeat(160), plannedDate = LocalDate.of(2026, 11, 20))
        assertTrue(EntryRules.errors(entry).isEmpty())
        assertTrue(EntryRules.errors(entry.copy(title = entry.title + "📚")).containsKey("title"))
        assertTrue(EntryRules.errors(entry.copy(title = "\u00A0\u2003")).containsKey("title"))
        assertTrue(EntryRules.errors(entry.copy(body = "я".repeat(10_000))).isEmpty())
        assertTrue(EntryRules.errors(entry.copy(body = "я".repeat(10_001))).containsKey("body"))
        assertEquals("Сдать отчёт завтра", EntryRules.cleanTitle("\u00A0Сдать\nотчёт\u2028завтра\u00A0"))
    }
}
