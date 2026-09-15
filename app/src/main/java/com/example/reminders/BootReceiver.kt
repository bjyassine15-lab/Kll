package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val scheduler = ReminderScheduler(context)
        val dao = AppDatabase.getInstance(context).studyMindDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val activeReminders = dao.getActiveRemindersSync()
                activeReminders.forEach { reminder ->
                    if (reminder.triggerTimeMillis > now && !reminder.isTriggered) {
                        scheduler.scheduleReminder(
                            reminderId = reminder.id,
                            title = reminder.title,
                            message = reminder.message,
                            triggerTimeMillis = reminder.triggerTimeMillis
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
