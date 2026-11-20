package ru.workweek.planner.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import ru.workweek.planner.MainActivity
import ru.workweek.planner.R
import ru.workweek.planner.data.DigestDelivery
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.PlannerDatabase
import ru.workweek.planner.data.SettingsRepository

object ReminderController {
    const val TARGET_DATE = "TARGET_DATE"
    const val CHANNEL_ID = "tomorrow_plans"
    private var engine: DigestEngine? = null
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError

    @Synchronized
    private fun engine(context: Context): DigestEngine = engine ?: run {
        val app = context.applicationContext
        DigestEngine(RoomDigestStore(app), AndroidDigestPlatform(app)).also { engine = it }
    }

    suspend fun refresh(context: Context, systemTimeChanged: Boolean = false) {
        safely { engine(context).refresh(systemTimeChanged) }
    }

    suspend fun update(context: Context, enabled: Boolean, time: LocalTime) {
        safely { engine(context).update(enabled, time) }
    }

    internal suspend fun callback(context: Context, alarm: DigestAlarm) {
        safely { engine(context).callback(alarm) }
    }

    private suspend fun safely(action: suspend () -> Unit) = withContext(Dispatchers.IO) {
        try {
            action()
            mutableError.value = null
        } catch (exception: Exception) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            mutableError.value = "Не удалось обновить сводку. Повторите попытку."
            Log.e("PlannerReminder", "Digest operation failed: ${exception.javaClass.simpleName}")
        }
    }

    fun availability(context: Context, enabled: Boolean): String = when {
        !enabled -> "Выключены"
        AndroidDigestPlatform(context).notificationsAvailable() -> "Включены"
        else -> "Запрещены системой"
    }

    fun openSystemSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal class RoomDigestStore(context: Context) : DigestStore {
    private val db = PlannerDatabase.open(context)
    private val preferences = SettingsRepository(context)
    override suspend fun settings() = preferences.reminderSettings()
    override suspend fun update(enabled: Boolean, time: LocalTime) = preferences.updateReminder(enabled, time)
    override suspend fun bumpRevision() = preferences.bumpReminderRevision()
    override suspend fun entries(date: LocalDate) = db.entries().digest(date)
    override suspend fun delivery(date: LocalDate) = db.deliveries().get(date)
    override suspend fun record(delivery: DigestDelivery) = db.deliveries().upsert(delivery)
}

internal class AndroidDigestPlatform(private val context: Context) : DigestPlatform {
    private val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val alarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    private fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannel(ReminderController.CHANNEL_ID,
            "Планы на завтра", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Вечерняя сводка завтрашних дел"
        })
    }

    override fun notificationsAvailable(): Boolean {
        ensureChannel()
        val permission = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(ReminderController.CHANNEL_ID).importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun pending(intent: Intent, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    override fun schedule(alarm: DigestAlarm) {
        val intent = Intent(context, DigestReceiver::class.java).setAction(DigestReceiver.ACTION_DIGEST)
            .putExtra("sendDate", alarm.sendDate.toString())
            .putExtra("targetDate", alarm.targetDate.toString())
            .putExtra("revision", alarm.revision)
            .putExtra("time", alarm.localTime.toString())
            .putExtra("trigger", alarm.triggerAt.toEpochMilli())
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt.toEpochMilli(),
            requireNotNull(pending(intent, PendingIntent.FLAG_UPDATE_CURRENT)))
        Log.i("PlannerReminder", "Scheduled ${alarm.sendDate} at ${alarm.triggerAt}, revision ${alarm.revision}")
    }

    override fun cancel() {
        val intent = Intent(context, DigestReceiver::class.java).setAction(DigestReceiver.ACTION_DIGEST)
        pending(intent, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }
    }

    @SuppressLint("MissingPermission") // Checked again for permission/channel revocation before notify.
    override fun publish(sendDate: LocalDate, targetDate: LocalDate, entries: List<Entry>) {
        check(notificationsAvailable()) { "Notifications unavailable" }
        ensureChannel()
        // The data URI makes an already displayed notification's destination immutable.
        val open = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("workweek://day/$targetDate"))
            .putExtra(ReminderController.TARGET_DATE, targetDate.toString())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val lines = DigestEngine.lines(entries)
        val notification = Notification.Builder(context, ReminderController.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(DigestEngine.title(entries.size))
            .setContentText(lines.first())
            .setStyle(Notification.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
            .setContentIntent(content)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(DigestEngine.notificationKey(sendDate), 1, notification)
        Log.i("PlannerReminder", "Published digest for $sendDate at ${java.time.Instant.now()}")
    }
}
