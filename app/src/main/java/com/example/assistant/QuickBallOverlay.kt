package com.example.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickBallOverlayContent(
    state: AssistantUiState,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onTapOrb: () -> Unit,
    onLongPressOrb: () -> Unit,
    onToggleListen: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleHandsFreeLoop: () -> Unit,
    onQuickAction: (action: String, param1: String) -> Unit,
    onOpenSettingsTarget: (String) -> Unit,
    onOpenAnnApp: () -> Unit,
    onMinimizePanel: () -> Unit,
    onStopService: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quickball_pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringColors = when (state.voiceState) {
        AssistantVoiceState.ARMED_VAD -> listOf(Color(0xFF14B8A6), Color(0xFF3B82F6))
        AssistantVoiceState.LISTENING -> listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
        AssistantVoiceState.THINKING -> listOf(Color(0xFFA855F7), Color(0xFFEC4899))
        AssistantVoiceState.SPEAKING -> listOf(Color(0xFF22C55E), Color(0xFF14B8A6))
        AssistantVoiceState.IDLE -> listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.isExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = state.voiceState == AssistantVoiceState.LISTENING ||
                        state.liveTranscript.isNotBlank() ||
                        state.voiceState == AssistantVoiceState.THINKING
                ) {
                    Surface(
                        color = Color(0xE6101728),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0x6660A5FA))
                    ) {
                        Text(
                            text = when {
                                state.liveTranscript.isNotBlank() -> state.liveTranscript
                                state.voiceState == AssistantVoiceState.THINKING -> "Ann is thinking..."
                                else -> "Listening..."
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 190.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .scale(if (state.voiceState == AssistantVoiceState.LISTENING) pulseScale.value else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(colors = listOf(Color(0xF0111827), Color(0xF01E293B)))
                        )
                        .border(2.dp, Brush.linearGradient(ringColors), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onTapOrb() },
                                onLongPress = { onLongPressOrb() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (state.voiceState) {
                        AssistantVoiceState.THINKING -> {
                            CircularProgressIndicator(
                                color = Color(0xFFE879F9),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        AssistantVoiceState.LISTENING -> {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Ann Capturing Speech",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        AssistantVoiceState.ARMED_VAD -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Ann Silent Mic Armed",
                                tint = Color(0xFF2DD4BF),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        AssistantVoiceState.SPEAKING -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Ann Speaking",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        AssistantVoiceState.IDLE -> {
                            Icon(
                                imageVector = Icons.Default.SettingsVoice,
                                contentDescription = "Open Ann QuickBall",
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF20D1322)),
                border = BorderStroke(1.2.dp, Brush.linearGradient(ringColors)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.width(308.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Draggable Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.x, dragAmount.y)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ringColors.first())
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Ann QuickBall",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (state.voiceState) {
                                        AssistantVoiceState.ARMED_VAD -> "Silent mic armed • Speak anytime"
                                        AssistantVoiceState.LISTENING -> "Capturing voice (silent auto-send)"
                                        AssistantVoiceState.THINKING -> "Thinking & acting..."
                                        AssistantVoiceState.SPEAKING -> "Speaking reply..."
                                        AssistantVoiceState.IDLE -> "Mic paused • Tap orb to arm"
                                    },
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onToggleTts, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (state.isTtsEnabled) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = "Toggle Speech",
                                    tint = if (state.isTtsEnabled) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onOpenAnnApp, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open Ann",
                                    tint = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(onClick = onMinimizePanel, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse QuickBall",
                                    tint = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Live Speech / Response HUD
                    Surface(
                        color = Color(0x16FFFFFF),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val headerInput = when {
                                state.liveTranscript.isNotBlank() -> "Hearing: \"${state.liveTranscript}\""
                                state.lastUserCommand.isNotBlank() -> "You: \"${state.lastUserCommand}\""
                                else -> null
                            }
                            if (headerInput != null) {
                                Text(
                                    text = headerInput,
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = state.lastReply,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.lastActionSummary.isNotBlank()) {
                                Text(
                                    text = "✓ ${state.lastActionSummary}",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Optional permission quick-fix pill if a requested action lacked system access
                    val permNeeded = state.needsPermissionHint
                    if (permNeeded != null || !state.isAccessibilityEnabled || !state.isWriteSettingsGranted) {
                        val targetPerm = permNeeded ?: if (!state.isWriteSettingsGranted) "write_settings" else "accessibility"
                        val label = when (targetPerm) {
                            "write_settings" -> "Allow Modify Settings"
                            "accessibility" -> "Enable Hand Control (Accessibility)"
                            "dnd" -> "Allow DND Policy Access"
                            else -> "Open Ann Permissions"
                        }
                        Surface(
                            color = Color(0x26F59E0B),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x66FBBF24)),
                            onClick = { onOpenSettingsTarget(targetPerm) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Primary Mic & Auto-Listen Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleListen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (
                                    state.voiceState == AssistantVoiceState.LISTENING ||
                                    state.voiceState == AssistantVoiceState.ARMED_VAD
                                ) {
                                    Color(0xFFDC2626)
                                } else {
                                    Color(0xFF2563EB)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = if (
                                    state.voiceState == AssistantVoiceState.LISTENING ||
                                    state.voiceState == AssistantVoiceState.ARMED_VAD
                                ) {
                                    Icons.Default.MicOff
                                } else {
                                    Icons.Default.Mic
                                },
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (
                                    state.voiceState == AssistantVoiceState.LISTENING ||
                                    state.voiceState == AssistantVoiceState.ARMED_VAD
                                ) {
                                    "Pause Mic"
                                } else {
                                    "Arm Mic"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            color = if (state.isHandsFreeLoopEnabled) Color(0x2622C55E) else Color(0x14FFFFFF),
                            border = BorderStroke(
                                1.dp,
                                if (state.isHandsFreeLoopEnabled) Color(0x6622C55E) else Color(0x26FFFFFF)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            onClick = onToggleHandsFreeLoop,
                            modifier = Modifier.height(42.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.isHandsFreeLoopEnabled) "Auto ON" else "Auto OFF",
                                    color = if (state.isHandsFreeLoopEnabled) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Hand Action Shortcut Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickPillButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "Back",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("nav", "back") }
                        )
                        QuickPillButton(
                            icon = Icons.Default.Home,
                            label = "Home",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("nav", "home") }
                        )
                        QuickPillButton(
                            icon = Icons.AutoMirrored.Filled.VolumeDown,
                            label = "Vol -",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("volume", "down") }
                        )
                        QuickPillButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Vol +",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("volume", "up") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hands-free active across apps",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                        TextButton(
                            onClick = onStopService,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Turn Off QuickBall",
                                color = Color(0xFFFCA5A5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPillButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0x16FFFFFF),
        border = BorderStroke(1.dp, Color(0x26FFFFFF)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
