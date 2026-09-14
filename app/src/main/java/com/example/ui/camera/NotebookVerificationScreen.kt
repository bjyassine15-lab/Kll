package com.example.ui.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.StudyMindApplication
import com.example.ai.NotebookVerificationResult
import com.example.ui.components.StudyMindTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookVerificationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyMindApplication
    val scope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<NotebookVerificationResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun processBitmap(bitmap: Bitmap) {
        selectedBitmap = bitmap
        isVerifying = true
        errorMessage = null
        scope.launch {
            val lastLesson = app.repository.allLessons.firstOrNull()?.firstOrNull()
            val expectedFormulas = lastLesson?.formulas?.lines() ?: listOf("v = d / t", "F = m * a")
            val keyConcepts = lastLesson?.keyConcepts?.lines() ?: listOf("القوة والحركة", "السرعة المتجهة")

            val result = app.orchestrator.verifyNotebook(bitmap, expectedFormulas, keyConcepts)
            isVerifying = false
            result.onSuccess { res ->
                verificationResult = res
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "فشل فحص الكراس"
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            processBitmap(bitmap)
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
                processBitmap(bitmap)
            }
        }
    }

    Scaffold(
        topBar = {
            StudyMindTopBar(
                title = "فحص الكراس وتثبيت الملاحظات",
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("التحقق من اكتمال تدوين الدرس في الكراس", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "صوّر صفحة الكراس وسيقوم المساعد بمطابقتها مع ما شرحه الأستاذ والتأكد من عدم نسيان أي قانون أو تعريف هام.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "صورة الكراس",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (isVerifying) {
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
                            CircularProgressIndicator(color = DeepIndigo)
                            Text("جاري قراءة خط اليد ومطابقة القوانين مع شرح الأستاذ...", fontSize = 13.sp)
                        }
                    }
                }

                errorMessage?.let {
                    Text(it, color = CoralRed, fontSize = 13.sp)
                }

                verificationResult?.let { res ->
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (res.completenessScore >= 70) EmeraldGreen.copy(alpha = 0.12f) else WarmAmber.copy(alpha = 0.12f)
                                )
                            ) {
                                val scorePct = (res.completenessScore * 100).toInt().coerceIn(0, 100)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("نسبة اكتمال الدرس المكتوب:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "$scorePct%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = if (scorePct >= 70) EmeraldGreen else WarmAmber
                                    )
                                }
                            }
                        }

                        item {
                            Text("الملاحظات والتوجيهات:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(res.feedback, fontSize = 13.sp, lineHeight = 20.sp)
                        }

                        if (res.missingFormulas.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("قوانين ومعادلات غير مكتوبة أو ناقصة:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CoralRed)
                                    for (f in res.missingFormulas) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text("• $f", modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        if (res.missingConcepts.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("مفاهيم وعناصر هامة لم يتم تدوينها:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WarmAmber)
                                    for (c in res.missingConcepts) {
                                        Text("• $c", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Buttons
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
                        .testTag("pick_notebook_gallery_button"),
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
                        .testTag("take_notebook_photo_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepIndigo)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("تصوير الكراس", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
