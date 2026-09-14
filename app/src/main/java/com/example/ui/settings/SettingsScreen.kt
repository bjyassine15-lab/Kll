package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.StudyMindApplication
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyMindApplication
    val scope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    var clearNotice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "الإعدادات والخصوصية",
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("مفاتيح الذكاء الاصطناعي (AI Studio & Picovoice)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val hasGemini = BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "GEMINI_API_KEY_HERE"
                        val hasPicovoice = BuildConfig.PICOVOICE_ACCESS_KEY.isNotBlank() && BuildConfig.PICOVOICE_ACCESS_KEY != "PICOVOICE_ACCESS_KEY_HERE"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Google Gemini Live & Flash:", fontSize = 13.sp)
                            Text(
                                if (hasGemini) "مهيأ بنجاح ✓" else "مفتاح افتراضي",
                                color = if (hasGemini) EmeraldGreen else WarmAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Picovoice Eagle (بصمة الصوت):", fontSize = 13.sp)
                            Text(
                                if (hasPicovoice) "مهيأ بنجاح ✓" else "مفتاح افتراضي",
                                color = if (hasPicovoice) EmeraldGreen else WarmAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("سياسة الخصوصية وحفظ البيانات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "جميع بياناتك الأكاديمية مخزنة محلياً في قاعدة بيانات هاتفك الآمنة (SQLite/Room). " +
                            "التسجيلات الصوتية في قاعة الدرس تُعالج لحظياً في الذاكرة دون تخزين ملفات صوتية غير مشفرة على القرص.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("إدارة الذاكرة والمسح", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CoralRed)
                        Text(
                            "يمكنك مسح سجل المحادثة مع المساعد الصوتي أو إعادة تعيين البيانات.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("مسح سجل محادثات المساعد")
                        }

                        if (clearNotice) {
                            Text("تم مسح سجل المحادثات بنجاح.", color = EmeraldGreen, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("حول StudyMind", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("الإصدار 1.0.0 - المساعد الدراسي التونسي الذكي", fontSize = 12.sp, color = Color.Gray)
                        Text("يدعم النظام التربوي التونسي، اللهجة التونسية، الفرنسية العلمية، والعربية الفصحى.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("تأكيد مسح السجل") },
                text = { Text("هل أنت متأكد من مسح جميع رسائل المحادثة مع المساعد الدراسي؟ لن يؤثر ذلك على جدول الحصص أو الفروض المسجلة.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                app.repository.clearMessages()
                                clearNotice = true
                                showClearConfirm = false
                            }
                        }
                    ) {
                        Text("نعم، امسح", color = CoralRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}
