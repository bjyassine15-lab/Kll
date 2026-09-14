package com.example.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LiveState
import com.example.data.local.entities.ChatMessage
import com.example.data.local.entities.StudyPlan
import com.example.ui.components.GlowingLiveOrb
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: MainViewModel,
    onOpenLiveAssistant: () -> Unit,
    onNavigateToClass: () -> Unit,
    onNavigateToScheduleCamera: () -> Unit,
    onNavigateToNotebookVerify: () -> Unit,
    onNavigateToTeacherEnrollment: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState(emptyList())
    val isThinking by viewModel.isAssistantThinking.collectAsState()
    val studentProfile by viewModel.studentProfile.collectAsState(null)
    val todayPlan by viewModel.todayPlan.collectAsState()
    val todaySchedule by viewModel.todaySchedule.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "StudyMind",
                actions = {
                    IconButton(
                        onClick = onNavigateToStatistics,
                        modifier = Modifier.testTag("stats_action_button")
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = "الإحصائيات")
                    }
                    IconButton(
                        onClick = onNavigateToMemory,
                        modifier = Modifier.testTag("memory_action_button")
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "الذاكرة المنظمة")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_action_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        },
        floatingActionButton = {
            // Glowing Live Assistant Orb FAB
            Box(
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("open_live_assistant_orb")
            ) {
                GlowingLiveOrb(
                    state = LiveState.LISTENING,
                    size = 62.dp,
                    onClick = onOpenLiveAssistant
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick Academic Actions Carousel
            QuickActionsHeader(
                studentName = studentProfile?.name ?: "يا بطل",
                todayScheduleCount = todaySchedule.size,
                todayPlan = todayPlan,
                onNavigateToClass = onNavigateToClass,
                onNavigateToScheduleCamera = onNavigateToScheduleCamera,
                onNavigateToNotebookVerify = onNavigateToNotebookVerify,
                onNavigateToTeacherEnrollment = onNavigateToTeacherEnrollment,
                onRefreshPlan = { viewModel.generateOrRefreshStudyPlan() }
            )

            // Conversation history
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyChatHeroCard(
                            studentName = studentProfile?.name ?: "يا بطل",
                            onSuggestionClick = { prompt ->
                                viewModel.sendMessage(prompt)
                            },
                            onOpenLive = onOpenLiveAssistant
                        )
                    }
                } else {
                    items(messages) { message ->
                        ChatMessageItem(message = message)
                    }
                }

                if (isThinking) {
                    item {
                        ThinkingIndicatorItem()
                    }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenLiveAssistant,
                        modifier = Modifier
                            .size(44.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            .testTag("mic_live_trigger_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "المساعد الصوتي",
                            tint = DeepIndigo
                        )
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                "تحدث أو اكتب لمساعدك الدراسي...",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                val msg = textInput
                                textInput = ""
                                viewModel.sendMessage(msg)
                            }
                        },
                        enabled = textInput.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (textInput.isNotBlank() && !isThinking) ElectricCyan else Color.LightGray.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "إرسال",
                            tint = if (textInput.isNotBlank()) MidnightSlate else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsHeader(
    studentName: String,
    todayScheduleCount: Int,
    todayPlan: StudyPlan?,
    onNavigateToClass: () -> Unit,
    onNavigateToScheduleCamera: () -> Unit,
    onNavigateToNotebookVerify: () -> Unit,
    onNavigateToTeacherEnrollment: () -> Unit,
    onRefreshPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active today plan banner (if available)
        if (todayPlan != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = ElectricCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = DeepIndigo, modifier = Modifier.size(18.dp))
                        Text(
                            text = "خطة اليوم: ${todayPlan.totalAvailableMinutes} دقيقة مذاكرة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepIndigo
                        )
                    }
                    TextButton(onClick = onRefreshPlan, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("تحديث", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Action pills row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ActionChipItem(
                    label = "استماع لحصة",
                    icon = Icons.Default.Mic,
                    containerColor = DeepIndigo,
                    contentColor = Color.White,
                    onClick = onNavigateToClass
                )
            }
            item {
                ActionChipItem(
                    label = "تصوير الجدول",
                    icon = Icons.Default.CalendarMonth,
                    containerColor = ElectricCyan,
                    contentColor = MidnightSlate,
                    onClick = onNavigateToScheduleCamera
                )
            }
            item {
                ActionChipItem(
                    label = "فحص الكراس",
                    icon = Icons.Default.CameraAlt,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onNavigateToNotebookVerify
                )
            }
            item {
                ActionChipItem(
                    label = "بصمة الأستاذ",
                    icon = Icons.Default.RecordVoiceOver,
                    containerColor = EmeraldGreen.copy(alpha = 0.15f),
                    contentColor = EmeraldGreen,
                    onClick = onNavigateToTeacherEnrollment
                )
            }
        }
    }
}

@Composable
private fun ActionChipItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Text(text = label, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyChatHeroCard(
    studentName: String,
    onSuggestionClick: (String) -> Unit,
    onOpenLive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(listOf(ElectricCyan, DeepIndigo)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = "مرحباً بك يا $studentName! أنا StudyMind",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Text(
                text = "مساعدك الدراسي الذكي الذي يدير حياتك الأكاديمية في الخلفية. يمكنك التحدث إليّ مباشرة بالصوت أو النص.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp
            )

            Button(
                onClick = onOpenLive,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MidnightSlate)
                Spacer(Modifier.width(8.dp))
                Text("ابدأ محادثة صوتية حية الآن", color = MidnightSlate, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("أو جرب أن تطلب مني:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                val suggestions = listOf(
                    "شنوة خطة المراجعة متاعي لليوم؟",
                    "عندي فرض فيزياء نهار الثلاثاء الجاي",
                    "ذكرني بحل تمارين الرياضيات بعد ساعتين",
                    "أنا نرجع للدار على الساعة 17:30"
                )
                suggestions.forEach { s ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSuggestionClick(s) }
                    ) {
                        Text(
                            text = s,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            color = DeepIndigo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.sender == "USER"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) DeepIndigo else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(elevation = if (isUser) 2.dp else 1.dp, shape = RoundedCornerShape(16.dp))
                .testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble")
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.text,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (message.hasAction && !message.actionPayload.isNullOrBlank()) {
                    Surface(
                        color = EmeraldGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(13.dp))
                            Text(
                                text = "تم تحديث البيانات المنظمة (${message.actionPayload})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicatorItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ElectricCyan
                )
                Text(
                    text = "StudyMind يفكر وينفذ الإجراءات...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
