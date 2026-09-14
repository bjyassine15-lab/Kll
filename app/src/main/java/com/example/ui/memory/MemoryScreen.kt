package com.example.ui.memory

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.*
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import com.example.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onNavigateBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState(null)
    val subjects by viewModel.subjects.collectAsState(emptyList())
    val teachers by viewModel.teachers.collectAsState(emptyList())
    val schedule by viewModel.schedule.collectAsState(emptyList())
    val exams by viewModel.exams.collectAsState(emptyList())
    val tasks by viewModel.tasks.collectAsState(emptyList())
    val weakAreas by viewModel.weakAreas.collectAsState(emptyList())
    val lessons by viewModel.lessons.collectAsState(emptyList())
    val reminders by viewModel.reminders.collectAsState(emptyList())

    val tabs = listOf(
        "الملف الشخصي",
        "جدول الحصص",
        "الفروض والامتحانات",
        "المهام والواجبات",
        "نقاط الضعف",
        "سجل الدروس",
        "المواد والأساتذة"
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "ذاكرة التطبيق والبيانات المنظمة",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> ProfileTabContent(profile = profile, onSave = { viewModel.saveProfile(it) })
                    1 -> ScheduleTabContent(schedule = schedule, onDelete = { viewModel.deleteScheduleEntry(it) })
                    2 -> ExamsTabContent(exams = exams, onDelete = { viewModel.deleteExam(it) })
                    3 -> TasksTabContent(
                        tasks = tasks,
                        onToggle = { id, comp -> viewModel.toggleTask(id, comp) },
                        onDelete = { viewModel.deleteTask(it) }
                    )
                    4 -> WeakAreasTabContent(weakAreas = weakAreas, onDelete = { viewModel.deleteWeakArea(it) })
                    5 -> LessonsTabContent(lessons = lessons, onDelete = { viewModel.deleteLesson(it) })
                    6 -> SubjectsTeachersTabContent(
                        subjects = subjects,
                        teachers = teachers,
                        onDeleteSubject = { viewModel.deleteSubject(it) },
                        onDeleteTeacher = { viewModel.deleteTeacher(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTabContent(
    profile: StudentProfile?,
    onSave: (StudentProfile) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var grade by remember(profile) { mutableStateOf(profile?.gradeLevel ?: "3ème Année Secondaire") }
    var arrivalTime by remember(profile) { mutableStateOf(profile?.homeArrivalTime ?: "17:30") }
    var sleepTime by remember(profile) { mutableStateOf(profile?.sleepTime ?: "22:30") }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("بيانات الطالب وجدول اليوم:", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم الطالب") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = grade,
            onValueChange = { grade = it },
            label = { Text("المستوى الدراسي / الشعبة") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = arrivalTime,
            onValueChange = { arrivalTime = it },
            label = { Text("وقت العودة للمنزل (مثال: 17:30)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = sleepTime,
            onValueChange = { sleepTime = it },
            label = { Text("وقت النوم المعتاد (مثال: 22:30)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val current = profile ?: StudentProfile()
                onSave(
                    current.copy(
                        name = name,
                        gradeLevel = grade,
                        homeArrivalTime = arrivalTime,
                        sleepTime = sleepTime
                    )
                )
                savedNotice = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_profile_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("حفظ وتحديث خطة المذاكرة", color = MidnightSlate, fontWeight = FontWeight.Bold)
        }

        if (savedNotice) {
            Text("تم حفظ التعديلات بنجاح!", color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScheduleTabContent(
    schedule: List<ScheduleEntry>,
    onDelete: (ScheduleEntry) -> Unit
) {
    if (schedule.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد حصص مسجلة في الجدول. يمكنك تصوير جدول الحصص بالكاميرا من الشاشة الرئيسية.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dayNames = listOf("", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
            items(schedule.sortedBy { it.dayOfWeek }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val dayStr = if (item.dayOfWeek in 1..7) dayNames[item.dayOfWeek] else "يوم ${item.dayOfWeek}"
                            Text("$dayStr: ${item.startTime} - ${item.endTime}", fontSize = 12.sp, color = DeepIndigo)
                            Text(item.subjectName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            item.teacherName?.let {
                                Text("الأستاذ: $it", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = CoralRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamsTabContent(
    exams: List<Exam>,
    onDelete: (Exam) -> Unit
) {
    if (exams.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد امتحانات مسجلة. يمكنك إخبار المساعد الصوتي بموعد الفرض ليضيفه تلقائياً.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
        }
    } else {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exams) { exam ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exam.subjectName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(exam.title, fontSize = 13.sp)
                            Text(
                                "التاريخ: ${sdf.format(Date(exam.examDate))} (${exam.time}) | المعامل: ${exam.coefficient}",
                                fontSize = 12.sp,
                                color = WarmAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { onDelete(exam) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = CoralRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksTabContent(
    tasks: List<Task>,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Task) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد مهام أو واجبات مسجلة حالياً.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onToggle(task.id, it) }
                            )
                            Column {
                                Text(
                                    task.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (task.subjectName.isNotBlank()) {
                                    Text(task.subjectName, fontSize = 12.sp, color = DeepIndigo)
                                }
                            }
                        }
                        IconButton(onClick = { onDelete(task) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = CoralRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakAreasTabContent(
    weakAreas: List<WeakArea>,
    onDelete: (WeakArea) -> Unit
) {
    if (weakAreas.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد نقاط ضعف مسجلة حالياً.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(weakAreas) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.subjectName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("المحور / الموضوع: ${item.topic}", fontSize = 13.sp)
                            Text(
                                "مستوى الصعوبة: ${item.severity}",
                                fontSize = 11.sp,
                                color = CoralRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = CoralRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonsTabContent(
    lessons: List<LessonRecord>,
    onDelete: (LessonRecord) -> Unit
) {
    if (lessons.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لم تقم بتسجيل أي حصة بعد.", color = Color.Gray)
        }
    } else {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lessons) { lesson ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lesson.subjectName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { onDelete(lesson) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = CoralRed)
                            }
                        }
                        Text("الأستاذ: ${lesson.teacherName} | التاريخ: ${sdf.format(Date(lesson.date))}", fontSize = 12.sp, color = Color.Gray)
                        Text(lesson.summary, fontSize = 13.sp, maxLines = 3)
                        if (lesson.formulas.isNotBlank()) {
                            Text("القوانين: ${lesson.formulas}", fontSize = 12.sp, color = DeepIndigo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectsTeachersTabContent(
    subjects: List<Subject>,
    teachers: List<Teacher>,
    onDeleteSubject: (Subject) -> Unit,
    onDeleteTeacher: (Teacher) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("المواد المسجلة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        items(subjects) { subj ->
            Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(subj.name, fontWeight = FontWeight.SemiBold)
                        Text("المعامل: ${subj.coefficient} | لغة التدريس: ${subj.defaultLanguage}", fontSize = 11.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { onDeleteSubject(subj) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = CoralRed)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("الأساتذة المسجلون:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        items(teachers) { teacher ->
            Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(teacher.name, fontWeight = FontWeight.SemiBold)
                        Text(teacher.subjectName, fontSize = 12.sp, color = DeepIndigo)
                    }
                    IconButton(onClick = { onDeleteTeacher(teacher) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = CoralRed)
                    }
                }
            }
        }
    }
}
