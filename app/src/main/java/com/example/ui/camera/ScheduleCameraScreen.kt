package com.example.ui.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.StudyMindApplication
import com.example.data.local.entities.ScheduleEntry
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCameraScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyMindApplication
    val scope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var extractedEntries by remember { mutableStateOf<List<ScheduleEntry>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            isProcessing = true
            errorMessage = null
            scope.launch {
                val result = app.orchestrator.importScheduleImage(bitmap)
                isProcessing = false
                result.onSuccess { entries ->
                    extractedEntries = entries
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "فشل تحليل الصورة"
                }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                selectedBitmap = bitmap
                isProcessing = true
                errorMessage = null
                scope.launch {
                    val result = app.orchestrator.importScheduleImage(bitmap)
                    isProcessing = false
                    result.onSuccess { entries ->
                        extractedEntries = entries
                    }.onFailure { err ->
                        errorMessage = err.localizedMessage ?: "فشل تحليل الصورة"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "استيراد جدول الحصص (Gemini Vision)",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("التقاط صورة لجدول الأوقات المدرسي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "التقط صورة واضحة لجدول الحصص (Emploi du temps) ليقوم المساعد بقراءة الأيام، التوقيت، والمواد تلقائياً.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "الصورة المختارة",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                            Text("جاري استخراج الحصص والتوقيت بواسطة Gemini Vision...", fontSize = 13.sp)
                        }
                    }
                }

                errorMessage?.let {
                    Text(it, color = CoralRed, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }

                if (extractedEntries.isNotEmpty()) {
                    Text("تم استخراج الحصص التالية وحفظها:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = EmeraldGreen)
                    val dayNames = listOf("", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(extractedEntries) { entry ->
                            Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val dayStr = if (entry.dayOfWeek in 1..7) dayNames[entry.dayOfWeek] else "يوم ${entry.dayOfWeek}"
                                    Text(entry.subjectName, fontWeight = FontWeight.SemiBold)
                                    Text("$dayStr: ${entry.startTime} - ${entry.endTime}", fontSize = 12.sp, color = DeepIndigo)
                                }
                            }
                        }
                    }
                }
            }

            // Buttons: Camera or Gallery
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pick_schedule_gallery_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("من المعرض")
                }

                Button(
                    onClick = { takePictureLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("take_schedule_photo_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MidnightSlate)
                    Spacer(Modifier.width(6.dp))
                    Text("التقاط صورة", color = MidnightSlate, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
