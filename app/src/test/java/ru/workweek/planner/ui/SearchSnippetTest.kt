package ru.workweek.planner.ui

import org.junit.Assert.*
import org.junit.Test

class SearchSnippetTest {
    @Test fun bodyMatchBeyondPreviewIsVisible() {
        val body = "Предисловие ".repeat(80) + "Отчёт клиента готов" + " послесловие".repeat(50)
        val fragment = searchSnippet(body, "ОТЧЕТ")
        assertTrue(fragment.contains("Отчёт клиента"))
        assertTrue(fragment.startsWith("…"))
        assertTrue(fragment.endsWith("…"))
    }

    @Test fun snippetDoesNotSplitEmojiSurrogates() {
        val fragment = searchSnippet("📅".repeat(200), "📅")
        assertFalse(fragment.dropLast(1).last().isHighSurrogate())
        assertTrue(searchSnippet("Короткое описание", "название").contains("Короткое описание"))
    }

    @Test fun normalizationExpansionUsesOriginalTextOffsets() {
        val body = "ﬃ".repeat(300) + " отчёт"
        val fragment = searchSnippet(body, "отчет")
        assertTrue(fragment.contains("отчёт"))
        assertTrue(fragment.contains("ﬃ"))
        val combining = "e\u0301".repeat(300) + " отчёт"
        assertTrue(searchSnippet(combining, "отчет").contains("отчёт"))
    }
}
