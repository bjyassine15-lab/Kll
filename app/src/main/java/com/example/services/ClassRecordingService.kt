package com.example.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.example.MainActivity
import com.example.R
import com.example.audio.ClassAudioBus
import com.example.audio.PcmAudioCapture
import com.example.reminders.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startService(intent) else context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val capture = PcmAudioCapture(sampleRate = 16000)
    private var recording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording(
                intent.getStringExtra(EXTRA_SUBJECT).orEmpty().ifBlank { "الحصة" },
                intent.getStringExtra(EXTRA_TEACHER).orEmpty()
            )
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(subject: String, teacher: String) {
        if (recording) return

        val notification = buildNotification(subject, teacher)
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

        val started = capture.start(serviceScope) { frame ->
            ClassAudioBus.emit(frame)
        }

        if (!started) {
            stopRecording()
            return
        }
        recording = true
    }

    private fun stopRecording() {
        if (recording) capture.stop()
        recording = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(subject: String, teacher: String) = NotificationCompat.Builder(
        this, NotificationHelper.CHANNEL_CLASS_SERVICE
    )
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("استماع إلى حصة $subject")
        .setContentText(if (teacher.isBlank()) "الميكروفون نشط" else "الأستاذ: $teacher • الميكروفون نشط")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        capture.stop()
        recording = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
