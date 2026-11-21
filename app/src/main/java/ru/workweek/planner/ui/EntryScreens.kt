package ru.workweek.planner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.workweek.planner.data.*
import java.time.LocalDate

@Composable
internal fun EntryCard(entry: Entry, appearance: AppearanceSettings, onOpen: () -> Unit, onCompleted: () -> Unit, search: Boolean = false, query: String = "") {
    val dial = rememberDial()
    val linkColor = MaterialTheme.colorScheme.primary
    val note = entry.noteText
    val displayText = remember(note, search, query, dial, linkColor) {
        if (search) AnnotatedString(searchSnippet(note, query))
        else phoneLinks(note, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)), dial)
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Checkbox(checked = entry.completed, onCheckedChange = { onCompleted() }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription = if (entry.completed) "Вернуть в невыполненные" else "Выполнить запись"
            })
            Surface(onClick = onOpen, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = displayText,
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

private const val PHONE_SEPARATOR = "[ \\u00A0\\u202F()-]*"
private val phoneNumber = Regex(
    "(?<![\\p{L}\\p{N}+])(?:\\+[1-9](?:${PHONE_SEPARATOR}[0-9]){6,14}|[78](?:${PHONE_SEPARATOR}[0-9]){10}|[0-9](?:${PHONE_SEPARATOR}[0-9]){9})(?![\\p{L}\\p{N}])"
)

private fun String.dialNumber() = filter { it == '+' || it in '0'..'9' }

@Composable
private fun rememberDial(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { number ->
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Приложение для звонков недоступно", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun phoneLinks(note: String, styles: TextLinkStyles, dial: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(note)
        for (match in phoneNumber.findAll(note)) {
            val number = match.value.dialNumber()
            addLink(LinkAnnotation.Clickable(number, styles) { dial(number) }, match.range.first, match.range.last + 1)
        }
    }

@Composable
private fun NotePhoneActions(noteText: String, enabled: Boolean) {
    val numbers = remember(noteText) { phoneNumber.findAll(noteText).map { it.value }.distinctBy { it.dialNumber() }.toList() }
    if (numbers.isEmpty()) return
    val dial = rememberDial()
    if (numbers.size == 1) {
        val number = numbers.single()
        TextButton(onClick = { dial(number.dialNumber()) }, enabled = enabled) { Text("Позвонить $number") }
    } else {
        var open by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { open = true }, enabled = enabled) { Text("Номера телефонов (${numbers.size})") }
            DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
                numbers.forEach { number ->
                    DropdownMenuItem(text = { Text(number) }, onClick = {
                        open = false
                        dial(number.dialNumber())
                    })
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
        NotePhoneActions(noteText, enabled = !busy)
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
