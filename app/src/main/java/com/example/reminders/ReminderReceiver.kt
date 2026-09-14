package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val reminderId = intent.getLongExtra("REMINDER_ID", -1L)
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "تذكير من StudyMind"
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: "لديك موعد دراسي مهم الآن"

        // Show notification
        NotificationHelper.showReminderNotification(
            context = context,
            notificationId = if (reminderId > 0) reminderId.toInt() else (System.currentTimeMillis() % 10000).toInt(),
            title = title,
            message = message
        )

        // Mark as triggered in database
        if (reminderId > 0) {
            val dao = AppDatabase.getInstance(context).studyMindDao()
            CoroutineScope(Dispatchers.IO).launch {
                dao.markReminderTriggered(reminderId)
            }
        }
    }
}
