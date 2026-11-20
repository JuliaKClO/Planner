package ru.workweek.planner.notifications

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.workweek.planner.data.DigestDelivery
import ru.workweek.planner.data.Entry
import ru.workweek.planner.data.ReminderSettings
import ru.workweek.planner.data.preview

data class DigestAlarm(
    val sendDate: LocalDate,
    val targetDate: LocalDate,
    val revision: Long,
    val localTime: LocalTime,
    val triggerAt: Instant,
)

interface DigestStore {
    suspend fun settings(): ReminderSettings
    suspend fun update(enabled: Boolean, time: LocalTime)
    suspend fun bumpRevision()
    suspend fun entries(date: LocalDate): List<Entry>
    suspend fun delivery(date: LocalDate): DigestDelivery?
    suspend fun record(delivery: DigestDelivery)
}

interface DigestPlatform {
    fun notificationsAvailable(): Boolean
    fun schedule(alarm: DigestAlarm)
    fun cancel()
    fun publish(sendDate: LocalDate, targetDate: LocalDate, entries: List<Entry>)
}

/** All entry points share one engine, including settings changes and concurrent broadcasts. */
class DigestEngine(
    private val store: DigestStore,
    private val platform: DigestPlatform,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()

    suspend fun update(enabled: Boolean, time: LocalTime) = mutex.withLock {
        store.update(enabled, time)
        reconcile(catchUp = true)
    }

    suspend fun refresh(systemTimeChanged: Boolean = false) = mutex.withLock {
        if (systemTimeChanged) store.bumpRevision()
        reconcile(catchUp = true)
    }

    suspend fun callback(alarm: DigestAlarm) = mutex.withLock {
        val settings = store.settings()
        // A stale callback owns neither publication nor the current alarm PendingIntent.
        if (alarm.revision != settings.scheduleRevision ||
            alarm.localTime != settings.localDeliveryTime || !settings.enabled
        ) return@withLock
        val today = LocalDate.now(clock.withZone(zone()))
        try {
            if (alarm.sendDate == today && alarm.targetDate == today.plusDays(1)) {
                publishIfDue(settings, today)
            }
        } finally {
            scheduleFuture(settings)
        }
    }

    private suspend fun reconcile(catchUp: Boolean) {
        val settings = store.settings()
        if (!settings.enabled) {
            platform.cancel()
            return
        }
        try {
            if (catchUp) publishIfDue(settings, LocalDate.now(clock.withZone(zone())))
        } finally {
            scheduleFuture(settings)
        }
    }

    private suspend fun publishIfDue(settings: ReminderSettings, today: LocalDate) {
        if (clock.instant().isBefore(moment(today, settings.localDeliveryTime, zone()))) return
        if (!platform.notificationsAvailable()) return
        if (store.delivery(today)?.deliveryState == "PUBLISHED") return
        val target = today.plusDays(1)
        val entries = store.entries(target)
        if (entries.isEmpty()) return
        if (LocalDate.now(clock.withZone(zone())) != today ||
            clock.instant().isBefore(moment(today, settings.localDeliveryTime, zone()))
        ) return
        // Persist an attempt first. Recovery reuses the same tag/id and onlyAlertOnce.
        val delivery = DigestDelivery(today, target, notificationKey(today), "ATTEMPTING", null)
        store.record(delivery)
        platform.publish(today, target, entries)
        store.record(delivery.copy(deliveryState = "PUBLISHED", publishedAtUtc = clock.instant()))
    }

    private fun scheduleFuture(settings: ReminderSettings) {
        val now = clock.instant()
        val currentZone = zone()
        val today = LocalDate.now(clock.withZone(currentZone))
        val day = if (now.isBefore(moment(today, settings.localDeliveryTime, currentZone))) {
            today
        } else {
            today.plusDays(1)
        }
        platform.schedule(DigestAlarm(day, day.plusDays(1), settings.scheduleRevision,
            settings.localDeliveryTime, moment(day, settings.localDeliveryTime, currentZone)))
    }

    companion object {
        // atZone shifts a gap by its duration and chooses the earlier offset in an overlap.
        fun moment(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
            date.atTime(time).atZone(zone).toInstant()

        fun notificationKey(date: LocalDate) = "digest-$date"

        fun title(count: Int): String {
            val noun = when {
                count % 100 in 11..14 -> "дел"
                count % 10 == 1 -> "дело"
                count % 10 in 2..4 -> "дела"
                else -> "дел"
            }
            return "Завтра у вас $count $noun"
        }

        fun lines(entries: List<Entry>): List<String> = entries.take(3).map {
            listOfNotNull(it.localTime?.toString(), it.preview).joinToString(" · ")
        } + if (entries.size > 3) listOf("И ещё ${entries.size - 3}") else emptyList()
    }
}
