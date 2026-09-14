package com.example.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import com.example.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MemoryViewModel,
    onNavigateBack: () -> Unit
) {
    val exams by viewModel.exams.collectAsState(emptyList())
    val tasks by viewModel.tasks.collectAsState(emptyList())
    val weakAreas by viewModel.weakAreas.collectAsState(emptyList())
    val lessons by viewModel.lessons.collectAsState(emptyList())

    val completedTasks = tasks.count { it.isCompleted }
    val totalTasks = tasks.size
    val taskRate = if (totalTasks > 0) (completedTasks * 100) / totalTasks else 100

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "الإحصائيات والتقدم الدراسي",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Task completion card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("إنجاز المهام والواجبات المنزلية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$completedTasks من أصل $totalTasks مهام منجزة", fontSize = 13.sp, color = Color.Gray)
                            Text("$taskRate%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EmeraldGreen)
                        }
                        LinearProgressIndicator(
                            progress = { if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 1f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldGreen
                        )
                    }
                }
            }

            item {
                // Quick Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavy)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("الحصص المسجلة", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text("${lessons.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavy)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("الفروض القادمة", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text("${exams.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                        }
                    }
                }
            }

            item {
                Text("العد التنازلي للفروض القادمة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (exams.isEmpty()) {
                item {
                    Text("لا توجد فروض قادمة مسجلة حالياً.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                val now = System.currentTimeMillis()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                items(exams.sortedBy { it.examDate }.size) { idx ->
                    val exam = exams[idx]
                    val diffDays = ((exam.examDate - now) / (1000 * 60 * 60 * 24)).toInt()
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
                                Text(exam.subjectName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(exam.title, fontSize = 13.sp)
                                Text(sdf.format(Date(exam.examDate)), fontSize = 11.sp, color = Color.Gray)
                            }
                            Surface(
                                color = if (diffDays <= 2) CoralRed.copy(alpha = 0.15f) else WarmAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (diffDays <= 0) "اليوم!" else "باقي $diffDays يوم",
                                    color = if (diffDays <= 2) CoralRed else WarmAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("نقاط الضعف النشطة المستهدفة بالتقوية:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (weakAreas.isEmpty()) {
                item {
                    Text("لا توجد نقاط ضعف مسجلة حالياً، أداء ممتاز!", color = EmeraldGreen, fontSize = 13.sp)
                }
            } else {
                items(weakAreas.size) { idx ->
                    val wa = weakAreas[idx]
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
                            Column {
                                Text(wa.subjectName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(wa.topic, fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(
                                "صعوبة: ${wa.severity}",
                                color = CoralRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
