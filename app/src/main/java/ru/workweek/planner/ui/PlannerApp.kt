package ru.workweek.planner.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.workweek.planner.data.*
import ru.workweek.planner.notifications.ReminderController
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private data class LoadedRows<T>(val request: String, val rows: List<T>)

@Composable
fun PlannerApp(repository: EntryRepository, preferences: SettingsRepository, targetDate: String? = null,
    deviceDate: () -> LocalDate = { LocalDate.now() }, onTargetConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by preferences.settings.collectAsState(initial = Settings())
    val appearance = settings.appearance
    var screen by rememberSaveable { mutableStateOf("week") }
    var selectedValue by rememberSaveable { mutableStateOf(bounded(deviceDate()).toString()) }
    var dayExpanded by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(deviceDate()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchLimit by rememberSaveable { mutableIntStateOf(50) }
    var origin by rememberSaveable { mutableStateOf("week") }
    var settingsOrigin by rememberSaveable { mutableStateOf("week") }
    var pendingWeek by rememberSaveable { mutableStateOf(false) }
    var draftId by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var planned by rememberSaveable { mutableStateOf(selectedValue) }
    var originalPlanned by rememberSaveable { mutableStateOf(selectedValue) }
    var time by rememberSaveable { mutableStateOf("") }
    var completed by rememberSaveable { mutableStateOf(false) }
    var remind by rememberSaveable { mutableStateOf(true) }
    var created by rememberSaveable { mutableStateOf(Instant.now().toString()) }
    var isNew by rememberSaveable { mutableStateOf(true) }
    var baseline by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var exitPrompt by rememberSaveable { mutableStateOf(false) }
    var deletePrompt by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var calendarEditor by rememberSaveable { mutableStateOf(false) }
    var calendarMonth by remember { mutableStateOf(startOfWeek(LocalDate.parse(selectedValue))) }
    var notificationStatus by remember { mutableStateOf("Выключены") }
    val reminderError by ReminderController.error.collectAsState()
    val selected = LocalDate.parse(selectedValue)
    val weekRequest = startOfWeek(selected).toString()
    val dayRequest = selectedValue
    val searchRequest = "$searchLimit\u0000$searchQuery"
    val weekLoad by remember(repository, weekRequest) { repository.observeWeek(startOfWeek(selected)).map { LoadedRows(weekRequest, it) }.catch { error = "Не удалось прочитать записи. Повторите попытку." } }.collectAsState(initial = LoadedRows("", emptyList()))
    val dayLoad by remember(repository, dayRequest) { repository.observeDay(selected).map { LoadedRows(dayRequest, it) }.catch { error = "Не удалось прочитать день." } }.collectAsState(initial = LoadedRows("", emptyList()))
    val searchLoad by remember(repository, searchRequest) { repository.observeSearch(searchQuery, searchLimit).map { LoadedRows(searchRequest, it) }.catch { error = "Не удалось выполнить поиск." } }.collectAsState(initial = LoadedRows("", emptyList()))
    val week = if (weekLoad.request == weekRequest) weekLoad.rows else emptyList()
    val day = if (dayLoad.request == selectedValue) dayLoad.rows else emptyList()
    val results = if (searchLoad.request == searchRequest && searchQuery == query) searchLoad.rows else emptyList()
    val marked by remember(repository, calendarMonth) { repository.observeMarkedDates(calendarMonth, YearMonth.from(calendarMonth).atEndOfMonth()).catch { error = "Не удалось прочитать календарь." } }.collectAsState(initial = emptyList())
    val searchState = rememberLazyListState()
    fun selectDate(date: LocalDate) {
        selectedValue = date.toString()
        dayExpanded = false
    }
    fun fingerprint() = listOf(title, body, planned, time, completed.toString(), remind.toString()).joinToString("\u0000")
    fun openEditor(entry: Entry?, date: LocalDate) {
        origin = screen
        draftId = entry?.id ?: UUID.randomUUID().toString()
        title = entry?.title.orEmpty(); body = entry?.body.orEmpty()
        planned = (entry?.plannedDate ?: date).toString(); time = entry?.localTime?.toString().orEmpty()
        originalPlanned = planned; pendingWeek = false
        completed = entry?.completed ?: false; remind = entry?.remindOnPreviousDay ?: true
        created = (entry?.createdAtUtc ?: Instant.now()).toString(); isNew = entry == null
        baseline = fingerprint(); fieldErrors = emptyMap(); error = null; screen = "editor"
    }
    fun leaveEditor() {
        if (pendingWeek) { selectDate(LocalDate.parse(planned)); screen = "week"; pendingWeek = false } else screen = origin
    }
    fun closeEditor() {
        if (busy) return
        if (fingerprint() != baseline) exitPrompt = true else leaveEditor()
    }
    fun save() {
        if (busy) return
        val parsedTime = if (time.isBlank()) null else runCatching { java.time.LocalTime.parse(time) }.getOrNull()
        val date = runCatching { LocalDate.parse(planned) }.getOrNull()
        val entry = Entry(id = draftId, title = title, body = body, plannedDate = date ?: selected,
            localTime = parsedTime, completed = completed, remindOnPreviousDay = remind, createdAtUtc = Instant.parse(created))
        val errors = EntryRules.errors(entry).toMutableMap()
        if (time.isNotBlank() && (parsedTime == null || !Regex("\\d{2}:\\d{2}").matches(time))) errors["time"] = "Введите время ЧЧ:ММ, от 00:00 до 23:59"
        if (date == null || !date.allowed()) errors["date"] = "Выберите дату с 1900 по 2100 год"
        fieldErrors = errors
        if (errors.isNotEmpty()) { exitPrompt = false; return }
        busy = true
        scope.launch {
            try {
                repository.save(entry)
                if (origin != "search" && entry.plannedDate != selected) selectDate(entry.plannedDate)
                leaveEditor(); exitPrompt = false; error = null
                runCatching { ReminderController.refresh(context) }.onFailure { error = "Запись сохранена. Не удалось восстановить расписание сводки." }
            } catch (_: Exception) { error = "Не удалось сохранить запись. Данные остались в форме, повторите попытку." }
            finally { busy = false }
        }
    }
    fun updateAfterMutation(operation: suspend () -> Unit) {
        scope.launch {
            try { operation(); runCatching { ReminderController.refresh(context) } }
            catch (_: Exception) { error = "Не удалось изменить запись. Повторите попытку." }
        }
    }
    LaunchedEffect(query) { delay(250); searchQuery = query; searchLimit = 50 }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { today = deviceDate() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(targetDate) {
        targetDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it.allowed() }?.let {
            selectDate(it); screen = "day"; calendar = false
        }; onTargetConsumed() }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, settings.reminder.enabled) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            today = deviceDate(); notificationStatus = ReminderController.availability(context, settings.reminder.enabled)
            scope.launch { runCatching { ReminderController.refresh(context) } }
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.reminder.enabled) { notificationStatus = ReminderController.availability(context, settings.reminder.enabled) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationStatus = ReminderController.availability(context, settings.reminder.enabled)
        scope.launch { runCatching { ReminderController.refresh(context) } }
    }
    BackHandler(enabled = !calendar && screen != "week") {
        when (screen) { "editor" -> { pendingWeek = false; closeEditor() }; "settings" -> screen = settingsOrigin; else -> screen = "week" }
    }
    PlannerTheme(appearance.theme.name, appearance.accent.name) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                if (calendar) CalendarScreen(
                    initial = if (calendarEditor) LocalDate.parse(planned) else selected, today, marked,
                    onMonth = { calendarMonth = it }, onCancel = { calendar = false }, onConfirm = {
                        if (calendarEditor) planned = it.toString() else selectDate(it)
                        calendar = false
                    }
                ) else {
                    if (screen != "editor") Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (screen != "week") Action("‹", "Назад") { screen = if (screen == "settings") settingsOrigin else "week" }
                        Text(when (screen) { "day" -> "Записи дня"; "search" -> "Поиск"; "settings" -> "Настройки"; else -> "Рабочая неделя" },
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = if (screen == "week") 12.dp else 0.dp))
                        if (screen == "week") {
                            Action("⌕", "Открыть поиск") { screen = "search" }
                            Action("⚙", "Открыть настройки") { settingsOrigin = "week"; screen = "settings" }
                        }
                    }
                    error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) { Text(message, color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { error = null }) { Text("Закрыть сообщение") } }
                    } }
                    val dayEntryCard: @Composable (Entry) -> Unit = { entry ->
                        EntryCard(entry, appearance, onOpen = { openEditor(entry, entry.plannedDate) },
                            onCompleted = { updateAfterMutation { repository.setCompleted(entry.id, !entry.completed) } })
                    }
                    when (screen) {
                        "week" -> WeekScreen(selected, today, week, appearance.fontSizeSp, appearance.fontFamily == NoteFontFamily.SERIF, appearance.datePlacement == DatePlacement.INSIDE,
                            onSelect = ::selectDate, expanded = dayExpanded, entries = day, dayLoading = dayLoad.request != dayRequest,
                            onToggle = { date ->
                                dayExpanded = date != selected || !dayExpanded
                                selectedValue = date.toString()
                            },
                            onAdd = { openEditor(null, it) }, onCalendar = { calendarEditor = false; calendar = true },
                            loading = weekLoad.request != weekRequest, entryContent = dayEntryCard)
                        "day" -> Column(Modifier.fillMaxSize()) {
                            Text(selected.format(fullDate), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { openEditor(null, selected) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text("Добавить запись") }
                            if (dayLoad.request != selectedValue) Text("Загрузка…", Modifier.padding(16.dp))
                            else if (day.isEmpty()) Text("Нет записей", Modifier.padding(16.dp))
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(day, key = { it.id }) { entry -> dayEntryCard(entry) }
                            }
                        }
                        "search" -> Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(query, { query = it }, label = { Text("Название или описание") }, modifier = Modifier.fillMaxWidth().padding(12.dp))
                            if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Очистить поиск") }
                            if (query.isBlank()) Text("Введите текст для поиска", Modifier.padding(16.dp))
                            else if (searchQuery != query || searchLoad.request != searchRequest) Text("Поиск…", Modifier.padding(16.dp))
                            else if (results.isEmpty()) Text("Ничего не найдено", Modifier.padding(16.dp))
                            LazyColumn(Modifier.weight(1f), state = searchState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(if (searchQuery == query) results else emptyList(), key = { _, entry -> entry.id }) { index, entry ->
                                    if (appearance.datePlacement == DatePlacement.SEPARATE &&
                                        (index == 0 || results[index - 1].plannedDate != entry.plannedDate)
                                    ) Text(entry.plannedDate.format(fullDate), style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(vertical = 6.dp))
                                    EntryCard(entry, appearance, onOpen = { openEditor(entry, entry.plannedDate) }, onCompleted = { updateAfterMutation { repository.setCompleted(entry.id, !entry.completed) } }, search = true, query = searchQuery)
                                }
                                if (results.size >= searchLimit) item { OutlinedButton(onClick = { searchLimit += 50 }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") } }
                            }
                        }
                        "settings" -> SettingsScreen(settings, notificationStatus, reminderError,
                            onAppearance = { scope.launch { runCatching { preferences.updateAppearance(it) }.onFailure { error = "Не удалось сохранить оформление." } } },
                            onReset = { scope.launch { runCatching { preferences.resetAppearance() }.onFailure { error = "Не удалось сбросить оформление." } } },
                            onReminder = { enabled, deliveryTime ->
                                val requestPermission = enabled && !settings.reminder.enabled && Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                scope.launch {
                                runCatching { ReminderController.update(context, enabled, deliveryTime) }.onFailure { error = "Не удалось сохранить настройки сводки." }
                                if (requestPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } }, onSystemSettings = { ReminderController.openSystemSettings(context) })
                        "editor" -> EditorScreen(title, body, planned, time, completed, remind, isNew, busy, fieldErrors,
                            settings.reminder.enabled, appearance,
                            onTitle = { title = it }, onBody = { body = it }, onTime = { time = it }, onCompleted = { completed = it }, onRemind = { remind = it },
                            onDate = { calendarEditor = true; calendar = true }, onSave = { save() }, onBack = { pendingWeek = false; closeEditor() },
                            onDelete = { deletePrompt = true }, onSettings = { settingsOrigin = "editor"; screen = "settings" },
                            onShowWeek = { pendingWeek = true; closeEditor() })
                    }
                }
            }
        }
        if (exitPrompt) AlertDialog(onDismissRequest = { if (!busy) { exitPrompt = false; pendingWeek = false } }, title = { Text("Сохранить изменения?") }, text = {
            Column { Button(onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
                TextButton(onClick = { exitPrompt = false; if (pendingWeek) planned = originalPlanned; leaveEditor() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Не сохранять") }
                TextButton(onClick = { exitPrompt = false; pendingWeek = false }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Продолжить редактирование") } }
        }, confirmButton = {})
        if (deletePrompt) AlertDialog(onDismissRequest = { if (!busy) deletePrompt = false }, title = { Text("Удалить запись?") }, confirmButton = {
            TextButton(onClick = {
                if (!busy) { busy = true; scope.launch {
                    try { repository.delete(draftId); deletePrompt = false; screen = origin; runCatching { ReminderController.refresh(context) } }
                    catch (_: Exception) { error = "Не удалось удалить запись."; deletePrompt = false }
                    finally { busy = false }
                } }
            }, enabled = !busy) { Text("Удалить") }
        }, dismissButton = { TextButton(onClick = { deletePrompt = false }, enabled = !busy) { Text("Отмена") } })
    }
}
