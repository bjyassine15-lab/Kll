package com.example.ui.classmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.Teacher
import com.example.transcription.ImportanceLevel
import com.example.transcription.SegmentCategory
import com.example.ui.components.SpeakerBadge
import com.example.ui.components.StudyMindTopBar
import com.example.ui.components.TeacherConfidenceMeter
import com.example.ui.theme.*
import com.example.viewmodel.ClassViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassListeningScreen(
    viewModel: ClassViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNotebookVerification: () -> Unit
) {
    val isListening by viewModel.isListening.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val teacherConfidence by viewModel.teacherConfidence.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingPostClass.collectAsState()
    val completedLesson by viewModel.completedLesson.collectAsState()

    val subjects by viewModel.availableSubjects.collectAsState(emptyList())
    val teachers by viewModel.availableTeachers.collectAsState(emptyList())

    var selectedSubjectName by remember { mutableStateOf("") }
    var selectedTeacher by remember { mutableStateOf<Teacher?>(null) }
    var showSetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(subjects) {
        if (selectedSubjectName.isEmpty() && subjects.isNotEmpty()) {
            selectedSubjectName = subjects.first().name
        }
    }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = if (isListening) "تسجيل الحصة نشط" else "الاستماع للحصة الدراسية",
                canNavigateBack = true,
                onNavigateBack = {
                    if (isListening) {
                        viewModel.stopAndAnalyze()
                    }
                    onNavigateBack()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isListening && completedLesson == null && !isAnalyzing) {
                // Pre-class setup view
                ClassStartSetupView(
                    subjects = subjects.map { it.name },
                    teachers = teachers,
                    selectedSubject = selectedSubjectName,
                    onSubjectSelected = { selectedSubjectName = it },
                    selectedTeacher = selectedTeacher,
                    onTeacherSelected = { selectedTeacher = it },
                    onStartClass = {
                        viewModel.startClass(
                            subjectName = selectedSubjectName.ifBlank { "درس عام" },
                            teacher = selectedTeacher
                        )
                    }
                )
            } else if (isAnalyzing) {
                // Post-class analysis loading screen
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "جاري تحليل الحصة واستخراج القوانين والواجبات...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "يتم استخدام نموذج Gemini لفصل كلام الأستاذ وتلخيص الدرس",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else if (completedLesson != null) {
                // Post-class outcome summary
                LessonSummaryOutcomeView(
                    lesson = completedLesson!!,
                    onDone = { onNavigateBack() },
                    onVerifyNotebook = onNavigateToNotebookVerification
                )
            } else {
                // Active class listening view
                ActiveListeningView(
                    elapsedSeconds = elapsedSeconds,
                    currentSpeaker = currentSpeaker,
                    teacherConfidence = teacherConfidence,
                    segments = segments,
                    onStop = { viewModel.stopAndAnalyze() },
                    onCancel = { viewModel.cancelClass() }
                )
            }
        }
    }
}

@Composable
private fun ClassStartSetupView(
    subjects: List<String>,
    teachers: List<Teacher>,
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit,
    selectedTeacher: Teacher?,
    onTeacherSelected: (Teacher?) -> Unit,
    onStartClass: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = ElectricCyan)
                        }
                        Column {
                            Text("الاستماع المباشر في قاعة الدرس", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("يتعرف النظام على صوت الأستاذ ويفرغ الدرس لحظياً", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Text("اختر مادة الحصة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 180.dp)
            ) {
                items(subjects) { subj ->
                    val isSelected = subj == selectedSubject
                    Surface(
                        color = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ElectricCyan else Color.LightGray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subj, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSubjectSelected(subj) }
                            )
                        }
                    }
                }
            }

            if (teachers.isNotEmpty()) {
                Text("اختر الأستاذ (للتعرف على صوته):", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(teachers) { teacher ->
                        val isSelected = teacher == selectedTeacher
                        Surface(
                            color = if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) EmeraldGreen else Color.LightGray.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(teacher.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (teacher.voiceEnrolled) "بصمة الصوت مسجلة (جاهز)" else "بصمة الصوت غير مسجلة بعد",
                                        fontSize = 11.sp,
                                        color = if (teacher.voiceEnrolled) EmeraldGreen else WarmAmber
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onTeacherSelected(teacher) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onStartClass,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_class_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = MidnightSlate)
            Spacer(Modifier.width(8.dp))
            Text("بدء الاستماع للحصة الآن", color = MidnightSlate, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ActiveListeningView(
    elapsedSeconds: Long,
    currentSpeaker: com.example.transcription.SpeakerType,
    teacherConfidence: Float,
    segments: List<com.example.transcription.TranscriptSegment>,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Timer & Background indicator
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkNavy),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(CoralRed, CircleShape)
                        )
                        Text(
                            text = "تسجيل حي - الميكروفون نشط في الخلفية",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = timerFormatted,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("class_timer_text")
                    )

                    // Speaker Badge & Confidence
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerBadge(currentSpeaker)
                    }

                    TeacherConfidenceMeter(
                        confidence = teacherConfidence,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Text("النقاط والتفريغ المباشر للحصة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (segments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("في انتظار حديث الأستاذ للتفريغ والتحليل...", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(segments.reversed()) { seg ->
                        val isHighPriority = seg.importance == ImportanceLevel.VERY_HIGH || seg.category == SegmentCategory.HOMEWORK
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighPriority) WarmAmber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakerBadge(seg.speakerType)
                                    if (seg.category != SegmentCategory.UNCERTAIN) {
                                        Text(
                                            text = when (seg.category) {
                                                SegmentCategory.FORMULA -> "قانون / معادلة"
                                                SegmentCategory.HOMEWORK -> "واجب منزلي"
                                                SegmentCategory.DEFINITION -> "تعريف"
                                                SegmentCategory.TEACHER_COMMAND -> "توجيه / كتابة"
                                                SegmentCategory.STUDENT_QUESTION -> "سؤال تلميذ"
                                                else -> "نقطة مهمة"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHighPriority) WarmAmber else DeepIndigo
                                        )
                                    }
                                }
                                Text(
                                    text = seg.text,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Actions: Stop & Finish / Cancel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("cancel_class_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("إلغاء", color = CoralRed)
            }

            Button(
                onClick = onStop,
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp)
                    .testTag("finish_class_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("إنهاء وتلخيص الحصة", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LessonSummaryOutcomeView(
    lesson: com.example.data.local.entities.LessonRecord,
    onDone: () -> Unit,
    onVerifyNotebook: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("تم تلخيص حصة ${lesson.subjectName} بنجاح!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EmeraldGreen)
                        Text("الأستاذ: ${lesson.teacherName} | التوقيت: ${lesson.startTime} - ${lesson.endTime}", fontSize = 12.sp)
                    }
                }
            }

            item {
                Text("ملخص الدرس:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(lesson.summary, fontSize = 14.sp, lineHeight = 22.sp)
            }

            if (lesson.teacherFocus.isNotBlank()) {
                item {
                    Text("ما ركّز عليه الأستاذ:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WarmAmber)
                    Text(lesson.teacherFocus, fontSize = 14.sp)
                }
            }

            if (lesson.formulas.isNotBlank()) {
                item {
                    Text("القوانين والمعادلات المستخرجة:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepIndigo)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lesson.formulas, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                    }
                }
            }

            if (lesson.homework.isNotBlank()) {
                item {
                    Text("الواجبات والتطبيقات المنزلية:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CoralRed)
                    Text(lesson.homework, fontSize = 14.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onVerifyNotebook,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("verify_notebook_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تصوير وفحص الكراس للتأكد من كتابة القوانين", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("إغلاق والعودة للمحادثة")
            }
        }
    }
}
