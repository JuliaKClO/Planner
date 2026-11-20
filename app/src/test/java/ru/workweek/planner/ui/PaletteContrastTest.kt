package ru.workweek.planner.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.workweek.planner.data.Accent

class PaletteContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test fun textAndSelectionMeetContrastThresholdsInAllEightPalettes() {
        for (dark in listOf(false, true)) for (accent in Accent.entries) {
            val c = plannerColors(dark, accent.name)
            val textPairs = listOf(
                c.onBackground to c.background, c.onSurface to c.surface, c.onSurfaceVariant to c.surface,
                c.primary to c.surface, c.primary to c.background, c.onPrimary to c.primary,
                c.onPrimaryContainer to c.primaryContainer, c.onErrorContainer to c.errorContainer,
            )
            for ((text, background) in textPairs) {
                val ratio = contrast(text, background)
                assertTrue("$accent dark=$dark text=$text background=$background contrast=$ratio", ratio >= 4.5)
            }
            assertTrue(contrast(c.outline, c.surface) >= 3.0)
        }
    }
}
