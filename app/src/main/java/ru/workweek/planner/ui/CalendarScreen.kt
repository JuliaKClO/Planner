package ru.workweek.planner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

internal fun parseDate(value: String): LocalDate? = ru.workweek.planner.domain.CalendarRules.parseDate(value)

@Composable
internal fun CalendarScreen(initial: LocalDate, today: LocalDate, marked: List<LocalDate>, onMonth: (LocalDate) -> Unit, onCancel: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(initial.toString()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf(initial.format(dateFormat)) }
    var error by rememberSaveable { mutableStateOf(false) }
    var jump by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableStateOf(initial.year.toString()) }
    var monthInput by rememberSaveable { mutableStateOf(initial.monthValue.toString()) }
    val month = YearMonth.parse(monthValue)
    val selection = LocalDate.parse(chosen)
    LaunchedEffect(monthValue) { onMonth(month.atDay(1)) }
    BackHandler { if (jump) jump = false else onCancel() }
    if (jump) {
        val y = year.toIntOrNull()
        val m = monthInput.toIntOrNull()
        val valid = y != null && y in 1900..2100 && m != null && m in 1..12
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Месяц и год", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(monthInput, { monthInput = it }, label = { Text("Месяц, 1–12") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(year, { year = it }, label = { Text("Год, 1900–2100") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = {
                monthValue = YearMonth.of(requireNotNull(y), requireNotNull(m)).toString()
                manual = false
                jump = false
            }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Показать") }
            OutlinedButton(onClick = { jump = false }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Выбор даты", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Action("‹", "Предыдущий месяц", month > YearMonth.of(1900, 1)) { monthValue = month.minusMonths(1).toString() }
            TextButton(onClick = { year = month.year.toString(); monthInput = month.monthValue.toString(); jump = true }, modifier = Modifier.weight(1f)) {
                Text(month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, russian) + " " + month.year)
            }
            Action("›", "Следующий месяц", month < YearMonth.of(2100, 12)) { monthValue = month.plusMonths(1).toString() }
        }
        TextButton(onClick = {
            if (manual) parseDate(input)?.let { chosen = it.toString(); monthValue = YearMonth.from(it).toString() }
            else input = selection.format(dateFormat)
            manual = !manual
        }) { Text(if (manual) "Календарь" else "Ввести дату вручную") }
        if (manual) {
            OutlinedTextField(input, { input = it; error = false }, label = { Text("ДД.ММ.ГГГГ") }, isError = error,
                supportingText = { if (error) Text("Введите существующую дату с 01.01.1900 по 31.12.2100") }, modifier = Modifier.fillMaxWidth())
        } else {
            val scale = LocalDensity.current.fontScale
            // Every grid cell is a separate 48 dp target. Text may wrap at large scale.
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, modifier = Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            val leading = month.atDay(1).dayOfWeek.value - 1
            repeat((leading + month.lengthOfMonth() + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { day ->
                        val number = week * 7 + day - leading + 1
                        if (number in 1..month.lengthOfMonth()) {
                            val date = month.atDay(number)
                            Surface(onClick = { chosen = date.toString(); input = date.format(dateFormat); error = false },
                                modifier = Modifier.weight(1f).heightIn(min = (48 * scale.coerceAtLeast(1f)).dp).semantics {
                                    contentDescription = "${date.format(fullDate)}${if (date == today) ", сегодня" else ""}${if (date == selection) ", выбрано" else ""}${if (date in marked) ", есть записи" else ""}"
                                }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = if (date == selection) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                color = if (date == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(number.toString(), fontSize = 14.sp)
                                    if (date in marked) Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else Spacer(Modifier.weight(1f).height(48.dp))
                    }
                }
            }
        }
        TextButton(onClick = { val date = bounded(today); chosen = date.toString(); input = date.format(dateFormat); monthValue = YearMonth.from(date).toString(); error = false }) { Text("Сегодня") }
        Button(onClick = {
            val date = if (manual) parseDate(input) else selection
            if (date == null) error = true else onConfirm(date)
        }, modifier = Modifier.fillMaxWidth()) { Text("Перейти") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}
