package com.example.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.LiveState
import com.example.ui.components.GlowingLiveOrb
import com.example.ui.theme.*
import com.example.viewmodel.LiveViewModel

@Composable
fun LiveAssistantDialog(
    viewModel: LiveViewModel,
    onDismiss: () -> Unit
) {
    val liveState by viewModel.liveState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.startLiveSession()
    }

    Dialog(
        onDismissRequest = {
            viewModel.endLiveSession()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_assistant_fullscreen"),
            color = MidnightSlate
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.endLiveSession()
                            onDismiss()
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .testTag("close_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (liveState) {
                                        LiveState.LISTENING -> ElectricCyan
                                        LiveState.SPEAKING -> EmeraldGreen
                                        LiveState.THINKING -> DeepIndigo
                                        LiveState.ERROR -> CoralRed
                                        else -> WarmAmber
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = "Gemini Live 3.1",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier
                            .background(
                                if (isMuted) CoralRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .testTag("toggle_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "كتم الصوت",
                            tint = if (isMuted) CoralRed else Color.White
                        )
                    }
                }

                // Center glowing Orb & State title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    GlowingLiveOrb(
                        state = liveState,
                        size = 150.dp,
                        onClick = {
                            if (liveState == LiveState.ERROR) {
                                viewModel.startLiveSession()
                            }
                        }
                    )

                    val statusText = when (liveState) {
                        LiveState.CONNECTING -> "جاري الاتصال بالمساعد الصوتي..."
                        LiveState.LISTENING -> if (isMuted) "الميكروفون مكتوم" else "أنا أستمع إليك... تحدث بحرية"
                        LiveState.THINKING -> "جاري المعالجة والتنفيذ..."
                        LiveState.SPEAKING -> "StudyMind يتحدث الآن..."
                        LiveState.ERROR -> errorMessage ?: "حدث خطأ في الاتصال"
                        LiveState.DISCONNECTED -> "الاتصال متوقف"
                    }

                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    // Real-time transcript box
                    if (transcript.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 140.dp),
                            color = DarkNavy.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = transcript,
                                color = LightCyan,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom suggestions & quick action bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val promptChips = listOf(
                        "شنوة برنامجي لليوم؟",
                        "غدوة عندي فرض فيزياء",
                        "ذكرني بعد ساعتين بحل التمارين",
                        "أنا ضعيف في الكهرباء",
                        "أنا نرجع للدار 17:30"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(promptChips) { chipText ->
                            SuggestionChip(
                                onClick = { viewModel.sendText(chipText) },
                                label = { Text(text = chipText, color = Color.White, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = DarkNavy
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = ElectricCyan.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }

                    // Optional manual text input for quiet environments
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("أو اكتب سؤالك للمساعد هنا...", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_text_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = DarkNavy,
                                unfocusedContainerColor = DarkNavy,
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    viewModel.sendText(textInput)
                                    textInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(ElectricCyan, CircleShape)
                                .testTag("send_live_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = MidnightSlate
                            )
                        }
                    }
                }
            }
        }
    }
}
