package ru.workweek.planner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.workweek.planner.data.*
import java.time.LocalTime

@Composable
private fun <T> Choice(title: String, values: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    values.forEach { (value, label) ->
        Surface(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier
                .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                .heightIn(min = 48.dp)) {
                RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                Text(label, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
internal fun SettingsScreen(settings: Settings, status: String, reminderError: String?, onAppearance: (AppearanceSettings) -> Unit, onReset: () -> Unit,
    onReminder: (Boolean, LocalTime) -> Unit, onSystemSettings: () -> Unit) {
    val a = settings.appearance
    var delivery by rememberSaveable(settings.reminder.localDeliveryTime.toString()) { mutableStateOf(settings.reminder.localDeliveryTime.toString()) }
    var timeError by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Завтрашние планы", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier
            .fillMaxWidth().heightIn(min = 48.dp).toggleable(value = settings.reminder.enabled, role = Role.Switch) {
                onReminder(it, settings.reminder.localDeliveryTime)
            }) {
            Text("Напоминать о планах на завтра", Modifier.weight(1f))
            Switch(checked = settings.reminder.enabled, onCheckedChange = null)
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reminderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Около ${settings.reminder.localDeliveryTime}. Android может задерживать доставку.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(delivery, { delivery = it; timeError = false }, label = { Text("Время сводки ЧЧ:ММ") }, isError = timeError,
            supportingText = { if (timeError) Text("Введите время от 00:00 до 23:59") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            val parsed = runCatching { LocalTime.parse(delivery) }.getOrNull()
            if (parsed == null || !Regex("\\d{2}:\\d{2}").matches(delivery)) timeError = true else onReminder(settings.reminder.enabled, parsed)
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить время") }
        TextButton(onClick = onSystemSettings) { Text("Уведомления в настройках Android") }
        HorizontalDivider()
        Text("Оформление", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Подготовить отчёт по итогам рабочей недели", fontFamily = if (a.fontFamily == NoteFontFamily.SERIF) FontFamily.Serif else FontFamily.SansSerif, fontSize = a.fontSizeSp.sp)
                Text(if (a.datePlacement == DatePlacement.INSIDE) "21.11 · 09:00" else "09:00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Choice("Тема", listOf(Theme.SYSTEM to "Системная", Theme.LIGHT to "Светлая", Theme.DARK to "Тёмная"), a.theme) { onAppearance(a.copy(theme = it)) }
        Choice("Шрифт записей", listOf(NoteFontFamily.SANS to "Без засечек", NoteFontFamily.SERIF to "С засечками"), a.fontFamily) { onAppearance(a.copy(fontFamily = it)) }
        Choice("Размер текста", listOf(14 to "14 sp", 16 to "16 sp", 18 to "18 sp"), a.fontSizeSp) { onAppearance(a.copy(fontSizeSp = it)) }
        Choice("Дата записи", listOf(DatePlacement.SEPARATE to "Отдельно в заголовке", DatePlacement.INSIDE to "Внутри карточки"), a.datePlacement) { onAppearance(a.copy(datePlacement = it)) }
        Choice("Акцент", listOf(Accent.BLUE to "Синий", Accent.TEAL to "Бирюзовый", Accent.PURPLE to "Фиолетовый", Accent.AMBER to "Янтарный"), a.accent) { onAppearance(a.copy(accent = it)) }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Сбросить оформление") }
    }
}
