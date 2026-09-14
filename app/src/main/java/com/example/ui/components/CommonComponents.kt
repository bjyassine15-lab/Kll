package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LiveState
import com.example.transcription.SpeakerType
import com.example.ui.theme.*

@Composable
fun GlowingLiveOrb(
    state: LiveState,
    size: Dp = 140.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (state == LiveState.SPEAKING || state == LiveState.LISTENING) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == LiveState.SPEAKING) 800 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    val (coreColors, auraColor) = when (state) {
        LiveState.DISCONNECTED -> listOf(DarkNavy, MidnightSlate) to Color.Gray
        LiveState.CONNECTING -> listOf(WarmAmber, DarkNavy) to WarmAmber
        LiveState.LISTENING -> listOf(ElectricCyan, DeepIndigo) to ElectricCyan
        LiveState.THINKING -> listOf(DeepIndigo, Color(0xFF7B2CBF)) to Color(0xFF7B2CBF)
        LiveState.SPEAKING -> listOf(EmeraldGreen, ElectricCyan) to EmeraldGreen
        LiveState.ERROR -> listOf(CoralRed, DarkNavy) to CoralRed
    }

    Box(
        modifier = Modifier
            .size(size * 1.4f)
            .testTag("glowing_live_orb")
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing aura
        Box(
            modifier = Modifier
                .size(size * pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(auraColor.copy(alpha = auraAlpha), Color.Transparent),
                        center = Offset.Unspecified,
                        radius = (size.value * 1.5f)
                    )
                )
        )

        // Inner glowing core
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (state == LiveState.SPEAKING) pulseScale else 1.0f)
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = auraColor)
                .clip(CircleShape)
                .background(Brush.linearGradient(coreColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    LiveState.SPEAKING -> Icons.Default.GraphicEq
                    LiveState.LISTENING -> Icons.Default.Mic
                    LiveState.THINKING -> Icons.Default.AutoAwesome
                    LiveState.CONNECTING -> Icons.Default.Sync
                    LiveState.ERROR -> Icons.Default.Warning
                    LiveState.DISCONNECTED -> Icons.Default.MicNone
                },
                contentDescription = "Live State",
                tint = Color.White,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

@Composable
fun SpeakerBadge(speakerType: SpeakerType, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label, icon) = when (speakerType) {
        SpeakerType.TEACHER -> Quad(EmeraldGreen.copy(alpha = 0.15f), EmeraldGreen, "صوت الأستاذ", Icons.Default.RecordVoiceOver)
        SpeakerType.STUDENT -> Quad(DeepIndigo.copy(alpha = 0.15f), DeepIndigo, "صوت التلميذ", Icons.Default.Person)
        SpeakerType.NOISE -> Quad(Color.Gray.copy(alpha = 0.12f), Color.Gray, "ضجيج / صمت", Icons.Default.VolumeMute)
        SpeakerType.MIXED -> Quad(WarmAmber.copy(alpha = 0.15f), WarmAmber, "أصوات متداخلة", Icons.Default.Group)
        SpeakerType.UNKNOWN -> Quad(Color.LightGray.copy(alpha = 0.2f), Color.DarkGray, "غير محدد", Icons.Default.HelpOutline)
    }

    Surface(
        modifier = modifier.testTag("speaker_badge_${speakerType.name}"),
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
            Text(text = label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun TeacherConfidenceMeter(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val pct = (confidence * 100).toInt().coerceIn(0, 100)
    val color = when {
        confidence >= 0.7f -> EmeraldGreen
        confidence >= 0.45f -> WarmAmber
        else -> Color.Gray
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تطابق صوت الأستاذ (Eagle AI):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "$pct%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("teacher_confidence_bar"),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMindTopBar(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "الرجوع"
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
