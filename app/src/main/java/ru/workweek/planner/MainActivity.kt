package ru.workweek.planner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.workweek.planner.data.EntryRepository
import ru.workweek.planner.data.PlannerDatabase
import ru.workweek.planner.data.SettingsRepository
import ru.workweek.planner.ui.PlannerApp

class MainActivity : ComponentActivity() {
    private var targetDate by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        targetDate = if (savedInstanceState == null) intent.getStringExtra("TARGET_DATE") else null
        val repository = EntryRepository(PlannerDatabase.open(applicationContext))
        val settings = SettingsRepository(applicationContext)
        setContent {
            PlannerApp(repository, settings, targetDate) { targetDate = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetDate = intent.getStringExtra("TARGET_DATE")
    }
}
