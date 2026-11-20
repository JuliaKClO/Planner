package ru.workweek.planner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.plannerDataStore by preferencesDataStore(name = "planner_settings")

enum class Theme { SYSTEM, LIGHT, DARK }
enum class NoteFontFamily { SANS, SERIF }
enum class DatePlacement { SEPARATE, INSIDE }
enum class Accent { BLUE, TEAL, PURPLE, AMBER }
enum class WeekColor(val label: String, private val light: Long, private val dark: Long) {
    BLUE("Синий", 0xFF2856B5, 0xFFADC6FF),
    TEAL("Бирюзовый", 0xFF006B60, 0xFF76D9C8),
    PURPLE("Фиолетовый", 0xFF7041A8, 0xFFD8B5FF),
    AMBER("Янтарный", 0xFF805600, 0xFFF0CA75),
    ROSE("Розовый", 0xFFA13258, 0xFFFFB0C8),
    GRAY("Серый", 0xFF555F71, 0xFFBBC6D9);

    fun color(dark: Boolean): Color = Color(if (dark) this.dark else light)
}

data class AppearanceSettings(
    val theme: Theme = Theme.SYSTEM,
    val fontFamily: NoteFontFamily = NoteFontFamily.SANS,
    val fontSizeSp: Int = 16,
    val datePlacement: DatePlacement = DatePlacement.SEPARATE,
    val accent: Accent = Accent.BLUE,
    val pastWeekColor: WeekColor = WeekColor.AMBER,
    val currentWeekColor: WeekColor = WeekColor.TEAL,
    val futureWeekColor: WeekColor = WeekColor.BLUE,
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val localDeliveryTime: LocalTime = LocalTime.of(20, 0),
    val scheduleRevision: Long = 0,
)

data class Settings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val reminder: ReminderSettings = ReminderSettings(),
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.plannerDataStore
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontFamily = stringPreferencesKey("font_family")
        val fontSize = intPreferencesKey("font_size")
        val datePlacement = stringPreferencesKey("date_placement")
        val accent = stringPreferencesKey("accent")
        val pastWeekColor = stringPreferencesKey("past_week_color")
        val currentWeekColor = stringPreferencesKey("current_week_color")
        val futureWeekColor = stringPreferencesKey("future_week_color")
        val reminders = booleanPreferencesKey("reminders_enabled")
        val minute = intPreferencesKey("reminder_minute")
        val revision = longPreferencesKey("schedule_revision")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            appearance = AppearanceSettings(
                theme = enumValueOr(p[Keys.theme], Theme.SYSTEM),
                fontFamily = enumValueOr(p[Keys.fontFamily], NoteFontFamily.SANS),
                fontSizeSp = (p[Keys.fontSize] ?: 16).takeIf { it in listOf(14, 16, 18) } ?: 16,
                datePlacement = enumValueOr(p[Keys.datePlacement], DatePlacement.SEPARATE),
                accent = enumValueOr(p[Keys.accent], Accent.BLUE),
                pastWeekColor = enumValueOr(p[Keys.pastWeekColor], WeekColor.AMBER),
                currentWeekColor = enumValueOr(p[Keys.currentWeekColor], WeekColor.TEAL),
                futureWeekColor = enumValueOr(p[Keys.futureWeekColor], WeekColor.BLUE),
            ),
            reminder = ReminderSettings(
                enabled = p[Keys.reminders] ?: false,
                localDeliveryTime = (p[Keys.minute] ?: 1200).coerceIn(0, 1439).let { LocalTime.of(it / 60, it % 60) },
                scheduleRevision = p[Keys.revision] ?: 0,
            ),
        )
    }

    suspend fun updateAppearance(appearance: AppearanceSettings) {
        require(appearance.fontSizeSp in listOf(14, 16, 18))
        store.edit { p ->
            p[Keys.theme] = appearance.theme.name
            p[Keys.fontFamily] = appearance.fontFamily.name
            p[Keys.fontSize] = appearance.fontSizeSp
            p[Keys.datePlacement] = appearance.datePlacement.name
            p[Keys.accent] = appearance.accent.name
            p[Keys.pastWeekColor] = appearance.pastWeekColor.name
            p[Keys.currentWeekColor] = appearance.currentWeekColor.name
            p[Keys.futureWeekColor] = appearance.futureWeekColor.name
        }
    }

    suspend fun resetAppearance() = updateAppearance(AppearanceSettings())
    suspend fun reminderSettings(): ReminderSettings = settings.first().reminder

    suspend fun updateReminder(enabled: Boolean, localDeliveryTime: LocalTime) {
        val minute = localDeliveryTime.hour * 60 + localDeliveryTime.minute
        store.edit { p ->
            if ((p[Keys.reminders] ?: false) != enabled || (p[Keys.minute] ?: 1200) != minute) {
                p[Keys.revision] = (p[Keys.revision] ?: 0) + 1
            }
            p[Keys.reminders] = enabled
            p[Keys.minute] = minute
        }
    }

    suspend fun bumpReminderRevision() {
        store.edit { p -> p[Keys.revision] = (p[Keys.revision] ?: 0) + 1 }
    }

    private inline fun <reified E : Enum<E>> enumValueOr(value: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default
}
