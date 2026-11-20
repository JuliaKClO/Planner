package ru.workweek.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.workweek.planner.domain.CalendarRules
import ru.workweek.planner.data.DaySummary
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.AppearanceSettings
import ru.workweek.planner.data.DatePlacement
import ru.workweek.planner.data.NoteFontFamily
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val russian = Locale.forLanguageTag("ru-RU")
internal val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
internal val shortDate = DateTimeFormatter.ofPattern("dd.MM")
internal val fullDate = DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", russian)
private val spokenDate = DateTimeFormatter.ofPattern("d MMMM uuuu", russian)
internal fun weekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    val monthDay = DateTimeFormatter.ofPattern("d MMM", russian)
    return when {
        start.year != end.year -> "${start.format(monthDay)} ${start.year} —\n${end.format(monthDay)} ${end.year}"
        start.month != end.month -> "${start.format(monthDay)} — ${end.format(monthDay)}\n${end.year}"
        else -> "${start.dayOfMonth} — ${end.format(DateTimeFormatter.ofPattern("d MMMM", russian))}\n${end.year}"
    }
}
private val accusativeWeekdays = listOf("понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье")
private fun entryCount(count: Int): String = when {
    count % 100 in 11..14 -> "$count записей"
    count % 10 == 1 -> "$count запись"
    count % 10 in 2..4 -> "$count записи"
    else -> "$count записей"
}
internal val minimumDate: LocalDate = CalendarRules.MIN_DATE
internal val maximumDate: LocalDate = CalendarRules.MAX_DATE
internal fun startOfWeek(date: LocalDate): LocalDate = CalendarRules.weekStart(date)
internal fun LocalDate.allowed() = CalendarRules.isSupported(this)
internal fun bounded(date: LocalDate) = CalendarRules.clamp(date)

@Composable
internal fun Action(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label }) {
        val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        if (symbol == "⌕" || symbol == "⚙") Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            if (symbol == "⌕") {
                drawCircle(color, radius = size.width * .29f, center = Offset(size.width * .40f, size.height * .40f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .92f, size.height * .92f), stroke)
            } else {
                drawCircle(color, radius = size.width * .28f, style = Stroke(stroke))
                drawCircle(color, radius = size.width * .08f, style = Stroke(stroke))
                repeat(8) { i ->
                    val angle = i * Math.PI / 4
                    val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                    drawLine(color, center + direction * size.width * .28f, center + direction * size.width * .43f, stroke * 1.4f)
                }
            }
        } else Text(symbol, fontSize = 24.sp, color = color)
    }
}

@Composable
internal fun WeekScreen(
    selected: LocalDate, today: LocalDate, days: List<DaySummary>, appearance: AppearanceSettings,
    onSelect: (LocalDate) -> Unit,
    expanded: Boolean, entries: List<Entry>, dayLoading: Boolean, onToggle: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit, onCalendar: () -> Unit, loading: Boolean,
    entryContent: @Composable (Entry) -> Unit
) {
    val start = startOfWeek(selected)
    val end = start.plusDays(6)
    val currentWeek = startOfWeek(today)
    val weekColor = when {
        start < currentWeek -> appearance.pastWeekColor
        start > currentWeek -> appearance.futureWeekColor
        else -> appearance.currentWeekColor
    }
    val background = MaterialTheme.colorScheme.background
    val indicator = weekColor.color(background.luminance() < 0.5f)
    val large = LocalDensity.current.fontScale > 1.1f
    val shift: (Int) -> Unit = { amount ->
        onSelect(CalendarRules.moveWeek(selected, amount))
    }
    Column(Modifier.fillMaxSize().background(lerp(background, indicator, 0.10f))) {
        if (large) {
            TextButton(onClick = onCalendar, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Выбрать дату" }) { Text("${start.format(dateFormat)} — ${end.format(dateFormat)}") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Action("‹", "Предыдущая неделя", start > startOfWeek(minimumDate)) { shift(-1) }
                TextButton(onClick = { onSelect(bounded(today)) }) { Text("Сегодня") }
                Action("›", "Следующая неделя", start < startOfWeek(maximumDate)) { shift(1) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Action("‹", "Предыдущая неделя", start > startOfWeek(minimumDate)) { shift(-1) }
                TextButton(onClick = onCalendar, modifier = Modifier.weight(1f).semantics { contentDescription = "Выбрать дату" }, contentPadding = PaddingValues(0.dp)) {
                    Text(weekLabel(start), fontSize = 14.sp, lineHeight = 17.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Action("›", "Следующая неделя", start < startOfWeek(maximumDate)) { shift(1) }
                TextButton(onClick = { onSelect(bounded(today)) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Сегодня") }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(3.dp).background(indicator).testTag("week-color-indicator"))
        var drag by remember { mutableFloatStateOf(0f) }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val rowMinimum = ((maxHeight - 32.dp) / 7).coerceAtLeast(56.dp)
            val listState = rememberLazyListState()
            LaunchedEffect(start, expanded, selected, dayLoading) {
                if (expanded && !dayLoading) listState.scrollToItem(selected.dayOfWeek.value - 1)
            }
            LazyColumn(
                Modifier.fillMaxSize().pointerInput(start, selected) {
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, delta -> drag += delta }, onDragEnd = {
                        if (kotlin.math.abs(drag) > 80.dp.toPx()) shift(if (drag < 0) 1 else -1)
                    })
                }, state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { offset ->
                    val date = start.plusDays(offset.toLong())
                    val open = expanded && date == selected
                    item(key = date.toEpochDay()) {
                        val summary = days.firstOrNull { it.date == date }
                        val first = summary?.first
                        val count = summary?.count ?: 0
                        val chosen = selected == date
                        val current = today == date
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(Modifier.heightIn(min = rowMinimum), verticalAlignment = Alignment.CenterVertically) {
                                Surface(onClick = { onToggle(date) }, enabled = date.allowed(), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f).semantics {
                                    stateDescription = if (open) "Раскрыто" else "Свёрнуто"
                                    contentDescription = "${date.format(fullDate)}, ${if (current) "сегодня, " else ""}${if (chosen) "выбрано, " else ""}${entryCount(count)}${if (first?.completed == true) ", первая запись выполнена" else ""}"
                                }) {
                                    Row(Modifier.heightIn(min = rowMinimum).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.width(if (large) 100.dp else 64.dp)) {
                                            Text("${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, russian).trimEnd('.').replaceFirstChar { it.titlecase(russian) }} ${date.dayOfMonth}", fontSize = 14.sp,
                                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                            if (current) Text("Сегодня", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(if (open) "Свернуть" else first?.title ?: if (loading) "Загрузка…" else "Нет записей", fontSize = appearance.fontSizeSp.sp,
                                                fontFamily = if (appearance.fontFamily == NoteFontFamily.SERIF) FontFamily.Serif else FontFamily.SansSerif,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = if (first == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                            if (!open && first != null) {
                                                val metadata = buildList {
                                                    first.localTime?.let { add(it.toString()) }
                                                    if (appearance.datePlacement == DatePlacement.INSIDE) add(date.format(shortDate))
                                                    if (first.completed) add("✓")
                                                    if (count > 1) add("Ещё ${count - 1}")
                                                }.joinToString(" · ")
                                                if (metadata.isNotEmpty()) Text(metadata, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                Action("+", "Добавить запись на ${accusativeWeekdays[date.dayOfWeek.value - 1]}, ${date.format(spokenDate)}", date.allowed()) { onAdd(date) }
                            }
                        }
                    }
                    if (open) {
                        if (dayLoading || entries.isEmpty()) item {
                            Text(if (dayLoading) "Загрузка…" else "Нет записей", modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
                        }
                        items(entries, key = { it.id }) { entry ->
                            Box(Modifier.padding(start = 8.dp)) { entryContent(entry) }
                        }
                    }
                }
            }
        }
    }
}
