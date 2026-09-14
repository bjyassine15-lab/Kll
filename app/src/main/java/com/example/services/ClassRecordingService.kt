package com.example.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.reminders.NotificationHelper

class ClassRecordingService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "com.example.studymind.START_CLASS_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.studymind.STOP_CLASS_RECORDING"
        const val EXTRA_SUBJECT = "EXTRA_SUBJECT"
        const val EXTRA_TEACHER = "EXTRA_TEACHER"

        fun startService(context: Context, subject: String, teacher: String) {
            val intent = Intent(context, ClassRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_TEACHER, teacher)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClassRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "الدرس"
                val teacher = intent.getStringExtra(EXTRA_TEACHER) ?: ""
                val notification = buildForegroundNotification(subject, teacher)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.NOTIFICATION_ID_SERVICE,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
                }
            }
            ACTION_STOP_RECORDING -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildForegroundNotification(subject: String, teacher: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val teacherText = if (teacher.isNotBlank()) " مع $teacher" else ""
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CLASS_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تسجيل حصة $subject$teacherText")
            .setContentText("الميكروفون نشط - جاري الاستماع وتحليل الدرس في الخلفية")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
