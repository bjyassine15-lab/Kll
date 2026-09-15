package com.example

import android.app.Application
import com.example.ai.GeminiLiveManager
import com.example.ai.GeminiTextProvider
import com.example.ai.GeminiVisionProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.StudyMindRepository
import com.example.lesson.LessonRecordingCoordinator
import com.example.orchestrator.StudyMindOrchestrator
import com.example.planner.StudyPlannerEngine
import com.example.reminders.NotificationHelper
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudyMindApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: StudyMindRepository
        private set

    lateinit var orchestrator: StudyMindOrchestrator
        private set

    lateinit var reminderScheduler: ReminderScheduler
        private set

    lateinit var liveManager: GeminiLiveManager
        private set

    lateinit var lessonCoordinator: LessonRecordingCoordinator
        private set

    lateinit var scheduleWatcher: com.example.schedule.ScheduleWatcher
        private set

    lateinit var toolExecutor: com.example.ai.tools.StudyMindToolExecutor
        private set

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 1. Create notification channels
        NotificationHelper.createNotificationChannels(this)

        // 2. Initialize Database & Repository
        database = AppDatabase.getInstance(this)
        repository = StudyMindRepository(database.studyMindDao())

        // 3. Seed default student profile if empty
        appScope.launch {
            repository.initDefaultProfileIfNeeded()
        }

        // 4. Initialize Reminders & Planners
        reminderScheduler = ReminderScheduler(this)
        val plannerEngine = StudyPlannerEngine(repository)

        // 5. Schedule Watcher
        scheduleWatcher = com.example.schedule.ScheduleWatcher(this, repository, reminderScheduler)
        appScope.launch {
            scheduleWatcher.refreshCurrentAndNextLessons()
            scheduleWatcher.syncDailyClassReminders()
        }

        // 6. Tool Executor
        toolExecutor = com.example.ai.tools.StudyMindToolExecutor(
            context = this,
            repository = repository,
            reminderScheduler = reminderScheduler,
            plannerEngine = plannerEngine,
            onStartClassListening = { subject, teacher ->
                // Start class recording service
                com.example.services.ClassRecordingService.startService(this, subject, teacher)
            },
            onStopClassListening = {
                com.example.services.ClassRecordingService.stopService(this)
            }
        )

        // 7. Initialize AI Providers
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val picovoiceKey = BuildConfig.PICOVOICE_ACCESS_KEY

        val textProvider = GeminiTextProvider(geminiApiKey)
        val visionProvider = GeminiVisionProvider(geminiApiKey)

        liveManager = GeminiLiveManager(
            customApiKey = geminiApiKey,
            onToolCall = { name, args ->
                toolExecutor.execute(name, args)
            }
        )

        lessonCoordinator = LessonRecordingCoordinator(
            context = this,
            repository = repository,
            picovoiceAccessKey = picovoiceKey,
            geminiApiKey = geminiApiKey
        )

        // 8. Central Orchestrator
        orchestrator = StudyMindOrchestrator(
            context = this,
            repository = repository,
            textProvider = textProvider,
            visionProvider = visionProvider,
            liveManager = liveManager,
            reminderScheduler = reminderScheduler,
            plannerEngine = plannerEngine,
            lessonCoordinator = lessonCoordinator,
            toolExecutor = toolExecutor
        )
    }
}
