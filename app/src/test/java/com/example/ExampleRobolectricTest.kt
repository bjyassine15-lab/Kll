package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.data.repository.StudyMindRepository
import com.example.planner.StudyPlannerEngine
import com.example.speaker.SpeakerClassificationEngine
import com.example.transcription.SpeakerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: StudyMindRepository
    private lateinit var plannerEngine: StudyPlannerEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudyMindRepository(db.studyMindDao())
        plannerEngine = StudyPlannerEngine(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StudyMind", appName)
    }

    @Test
    fun `test profile initialization and update`() = runBlocking {
        repository.initDefaultProfileIfNeeded()
        val profile = repository.getStudentProfileSync()
        assertNotNull(profile)
        assertEquals("الطالب", profile?.name)

        repository.saveProfile(profile!!.copy(homeArrivalTime = "18:00"))
        val updated = repository.getStudentProfileSync()
        assertEquals("18:00", updated?.homeArrivalTime)
    }

    @Test
    fun `test deterministic study planner generation`() = runBlocking {
        repository.initDefaultProfileIfNeeded()
        
        // Add an upcoming physics exam
        val examDate = System.currentTimeMillis() + (2 * 24 * 60 * 60 * 1000)
        repository.insertExam(
            Exam(
                subjectId = 1L,
                subjectName = "الفيزياء",
                title = "فرض مراقبة عدد 1",
                examDate = examDate,
                time = "08:00",
                coefficient = 4.0
            )
        )

        // Generate study plan
        val plan = plannerEngine.generateDailyPlan()
        assertNotNull(plan)
        assertTrue(plan.totalAvailableMinutes > 0)
        assertTrue(plan.planBlocksJson.contains("الفيزياء"))
    }

    @Test
    fun `test speaker classification engine temporal smoothing`() {
        val engine = SpeakerClassificationEngine()

        // 1. Silence / no voice active
        val resSilence = engine.classify(isVoiceActive = false, eagleScores = null, rmsEnergy = 50.0)
        assertEquals(SpeakerType.NOISE, resSilence.type)

        // 2. High eagle score for teacher
        val resTeacher = engine.classify(isVoiceActive = true, eagleScores = floatArrayOf(0.85f), rmsEnergy = 800.0)
        assertEquals(SpeakerType.TEACHER, resTeacher.type)
        assertTrue(resTeacher.teacherMatchScore > 0.6f)

        // 3. Reset and feed frames with low eagle score -> Student voice after hysteresis window
        engine.reset()
        var resStudent = engine.classify(isVoiceActive = true, eagleScores = floatArrayOf(0.10f), rmsEnergy = 800.0)
        assertEquals(SpeakerType.UNKNOWN, resStudent.type) // Not enough frames yet
        repeat(4) {
            resStudent = engine.classify(isVoiceActive = true, eagleScores = floatArrayOf(0.10f), rmsEnergy = 800.0)
        }
        assertEquals(SpeakerType.STUDENT, resStudent.type)
    }
}
