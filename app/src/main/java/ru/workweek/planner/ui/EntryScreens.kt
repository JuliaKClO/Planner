package ru.workweek.planner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.workweek.planner.data.*
import java.time.LocalDate

@Composable
internal fun EntryCard(entry: Entry, appearance: AppearanceSettings, onOpen: () -> Unit, onCompleted: () -> Unit, search: Boolean = false, query: String = "") {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Checkbox(checked = entry.completed, onCheckedChange = { onCompleted() }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription = if (entry.completed) "Вернуть в невыполненные" else "Выполнить запись"
            })
            Surface(onClick = onOpen, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (search) searchSnippet(entry.noteText, query) else entry.noteText,
                        fontFamily = if (appearance.fontFamily == NoteFontFamily.SERIF) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = appearance.fontSizeSp.sp
                    )
                    val metadata = buildList {
                        if (appearance.datePlacement == DatePlacement.INSIDE) add(entry.plannedDate.format(dateFormat))
                        entry.localTime?.let { add(it.toString()) }
                        if (entry.completed) add("Выполнено")
                    }.joinToString(" · ")
                    if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun searchSnippet(body: String, query: String): String {
    val normalized = ru.workweek.planner.domain.SearchRules.normalize(body)
    val index = ru.workweek.planner.domain.SearchRules.words(query).map { normalized.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
    // Map the normalized match back to the original text, including NFKC expansions
    // and combining characters. Prefix lengths preserve the normalization contract.
    var low = 0
    var high = body.length
    while (low < high) {
        val middle = (low + high) / 2
        val prefixLength = ru.workweek.planner.domain.SearchRules.normalize(body.substring(0, middle)).length
        if (prefixLength <= index) low = middle + 1 else high = middle
    }
    val originalIndex = (low - 1).coerceAtLeast(0)
    var start = (originalIndex - 45).coerceIn(0, body.length)
    if (start < body.length && start > 0 && body[start].isLowSurrogate()) start--
    var end = (start + 180).coerceAtMost(body.length)
    if (end < body.length && end > 0 && body[end - 1].isHighSurrogate()) end--
    return (if (start > 0) "…" else "") + body.substring(start, end) + if (end < body.length) "…" else ""
}

@Composable
internal fun EditorScreen(
    noteText: String, planned: String, time: String, completed: Boolean, remind: Boolean,
    isNew: Boolean, busy: Boolean, errors: Map<String, String>, reminderEnabled: Boolean, appearance: AppearanceSettings,
    onNoteText: (String) -> Unit, onTime: (String) -> Unit,
    onCompleted: (Boolean) -> Unit, onRemind: (Boolean) -> Unit, onDate: () -> Unit,
    onSave: () -> Unit, onBack: () -> Unit, onDelete: () -> Unit, onSettings: () -> Unit, onShowWeek: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val style = LocalTextStyle.current.copy(fontSize = appearance.fontSizeSp.sp, fontFamily = if (appearance.fontFamily == NoteFontFamily.SERIF) FontFamily.Serif else FontFamily.SansSerif)
    LaunchedEffect(Unit) { if (isNew) focus.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isNew) "Новая запись" else "Редактирование", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(noteText, onNoteText, label = { Text("Текст заметки") }, textStyle = style, minLines = 6,
            enabled = !busy, modifier = Modifier.fillMaxWidth().focusRequester(focus), isError = errors.containsKey("body"), supportingText = { errors["body"]?.let { Text(it) } })
        OutlinedButton(onClick = onDate, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Дата · ${LocalDate.parse(planned).format(dateFormat)}") }
        errors["date"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(time, onTime, label = { Text("Время ЧЧ:ММ, необязательно") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey("time"), supportingText = { errors["time"]?.let { Text(it) } })
        if (time.isNotEmpty()) TextButton(onClick = { onTime("") }, enabled = !busy) { Text("Убрать время") }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(completed, enabled = !busy, role = Role.Checkbox, onValueChange = onCompleted),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(completed, null, enabled = !busy, modifier = Modifier.padding(end = 12.dp))
            Text("Выполнено")
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(remind, enabled = !busy, role = Role.Checkbox, onValueChange = onRemind),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(remind, null, enabled = !busy, modifier = Modifier.padding(end = 12.dp))
            Text("Напомнить накануне")
        }
        if (!reminderEnabled) TextButton(onClick = onSettings, enabled = !busy) { Text("Сводка выключена · Настройки") }
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Сохранение…" else "Сохранить") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
        if (!isNew) {
            TextButton(onClick = onShowWeek, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Показать в неделе") }
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Удалить запись", color = MaterialTheme.colorScheme.error) }
        }
    }
}
