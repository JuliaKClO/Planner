package ru.workweek.planner.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                if (intent.action == ACTION_DIGEST) {
                    Log.i("PlannerReminder", "Callback received at ${Instant.now()}")
                    val alarm = DigestAlarm(
                        LocalDate.parse(requireNotNull(intent.getStringExtra("sendDate"))),
                        LocalDate.parse(requireNotNull(intent.getStringExtra("targetDate"))),
                        intent.getLongExtra("revision", -1),
                        LocalTime.parse(requireNotNull(intent.getStringExtra("time"))),
                        Instant.ofEpochMilli(intent.getLongExtra("trigger", 0)),
                    )
                    ReminderController.callback(context, alarm)
                } else {
                    val changed = intent.action == Intent.ACTION_TIME_CHANGED ||
                        intent.action == Intent.ACTION_TIMEZONE_CHANGED
                    ReminderController.refresh(context, changed)
                }
            } catch (exception: Exception) {
                Log.e("PlannerReminder", "Receiver failed: ${exception.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DIGEST = "ru.workweek.planner.DIGEST"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
