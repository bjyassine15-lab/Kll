package com.example.ui.teachers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entities.Teacher
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import com.example.viewmodel.MemoryViewModel
import com.example.viewmodel.TeacherEnrollmentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherEnrollmentScreen(
    memoryViewModel: MemoryViewModel,
    enrollmentViewModel: TeacherEnrollmentViewModel,
    onNavigateBack: () -> Unit
) {
    val teachers by memoryViewModel.teachers.collectAsState(emptyList())
    val subjects by memoryViewModel.subjects.collectAsState(emptyList())

    val isEnrolling by enrollmentViewModel.isEnrolling.collectAsState()
    val progress by enrollmentViewModel.enrollmentProgress.collectAsState()
    val statusMessage by enrollmentViewModel.statusMessage.collectAsState()
    val isCompleted by enrollmentViewModel.isCompleted.collectAsState()

    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var newTeacherName by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("") }

    var enrollingTeacher by remember { mutableStateOf<Teacher?>(null) }

    LaunchedEffect(subjects) {
        if (selectedSubjectName.isEmpty() && subjects.isNotEmpty()) {
            selectedSubjectName = subjects.first().name
        }
    }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "بصمة صوت الأساتذة (Eagle AI)",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showAddTeacherDialog = true },
                        modifier = Modifier.testTag("add_teacher_icon")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "إضافة أستاذ")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTeacherDialog = true },
                containerColor = ElectricCyan,
                modifier = Modifier.testTag("add_teacher_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة أستاذ", tint = MidnightSlate)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (teachers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "لم تقم بإضافة أي أستاذ بعد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "أضف أستاذك وسجل صوته ليتعرف عليه المساعد تلقائياً أثناء الحصص",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "تقنية التعرف على نبرة الصوت",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DeepIndigo
                                )
                                Text(
                                    "يقوم محرك Picovoice Eagle بتحليل الترددات الصوتية للأستاذ لعزله عن ضجيج التلاميذ بدقة عالية.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    items(teachers) { teacher ->
                        TeacherCardItem(
                            teacher = teacher,
                            onStartEnroll = {
                                enrollingTeacher = teacher
                                enrollmentViewModel.startEnrollment(teacher)
                            },
                            onDelete = {
                                memoryViewModel.deleteTeacher(teacher)
                            }
                        )
                    }
                }
            }

            // Dialog for adding teacher
            if (showAddTeacherDialog) {
                AlertDialog(
                    onDismissRequest = { showAddTeacherDialog = false },
                    title = { Text("إضافة أستاذ جديد", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = newTeacherName,
                                onValueChange = { newTeacherName = it },
                                label = { Text("اسم الأستاذ") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("teacher_name_input")
                            )

                            Text("المادة التي يدرّسها:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                                items(subjects) { subj ->
                                    val isSelected = subj.name == selectedSubjectName
                                    Surface(
                                        color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedSubjectName = subj.name }
                                            )
                                            Text(subj.name, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTeacherName.isNotBlank()) {
                                    val subject = subjects.find { it.name == selectedSubjectName }
                                    memoryViewModel.addTeacher(
                                        name = newTeacherName,
                                        subjectId = subject?.id ?: 0L,
                                        subjectName = selectedSubjectName
                                    )
                                    newTeacherName = ""
                                    showAddTeacherDialog = false
                                }
                            },
                            modifier = Modifier.testTag("confirm_add_teacher")
                        ) {
                            Text("إضافة")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTeacherDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Enrollment Live Modal
            if (enrollingTeacher != null) {
                Dialog(onDismissRequest = {
                    enrollmentViewModel.stopEnrollment()
                    enrollingTeacher = null
                }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("enrollment_dialog")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "تدريب بصمة صوت الأستاذ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                enrollingTeacher?.name ?: "",
                                fontSize = 15.sp,
                                color = DeepIndigo,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Animated Microphone Ring
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(
                                        if (isCompleted) EmeraldGreen.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isCompleted) EmeraldGreen else ElectricCyan,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            // Progress Bar
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("نسبة اكتمال البصمة:", fontSize = 12.sp)
                                    Text("${progress.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (isCompleted) EmeraldGreen else ElectricCyan
                                )
                            }

                            Text(
                                text = statusMessage,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = if (isCompleted) EmeraldGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )

                            if (isCompleted) {
                                Button(
                                    onClick = {
                                        enrollingTeacher = null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("done_enrollment_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Text("تم بنجاح")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        enrollmentViewModel.stopEnrollment()
                                        enrollingTeacher = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إيقاف التدريب")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherCardItem(
    teacher: Teacher,
    onStartEnroll: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teacher_card_${teacher.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(teacher.subjectName, fontSize = 13.sp, color = DeepIndigo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (teacher.voiceEnrolled) EmeraldGreen else WarmAmber,
                                CircleShape
                            )
                    )
                    Text(
                        if (teacher.voiceEnrolled) "البصمة الصوتية مسجلة ومفعلة" else "غير مسجلة",
                        fontSize = 11.sp,
                        color = if (teacher.voiceEnrolled) EmeraldGreen else WarmAmber
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onStartEnroll) {
                    Icon(
                        imageVector = if (teacher.voiceEnrolled) Icons.Default.Refresh else Icons.Default.Mic,
                        contentDescription = "تسجيل الصوت",
                        tint = ElectricCyan
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = CoralRed
                    )
                }
            }
        }
    }
}
