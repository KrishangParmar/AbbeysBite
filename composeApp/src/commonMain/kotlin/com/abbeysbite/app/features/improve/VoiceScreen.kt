package com.abbeysbite.app.features.improve

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import org.koin.compose.viewmodel.koinViewModel

/** Dedicated minimal voice mode: thumbnail, breathing orb, live transcript, stop. */
@Composable
fun VoiceScreen(
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: VoiceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startConversation()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = viewModel::toggleMute) {
                    Icon(
                        if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (state.muted) "Unmute" else "Mute",
                        tint = if (state.muted) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.stopConversation(); onClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close voice mode")
                }
            }
            viewModel.session.imageBytes?.let { bytes ->
                Image(
                    painter = rememberAsyncImagePainter(bytes),
                    contentDescription = "Current meal",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(Modifier.height(Dimens.xxl))

        VoiceOrb(phase = state.phase)

        Spacer(Modifier.height(Dimens.xl))

        AnimatedContent(
            targetState = phaseLabel(state),
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "voiceStatus",
        ) { label ->
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Dimens.lg))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (val phase = state.phase) {
                is VoicePhase.PermissionNeeded -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Voice mode needs microphone access.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Dimens.md))
                    PrimaryButton(text = "Try again", onClick = viewModel::retry)
                }

                is VoicePhase.PremiumNeeded -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Voice conversations are part of Premium.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Dimens.md))
                    PrimaryButton(text = "See Premium", onClick = onOpenPaywall)
                }

                is VoicePhase.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phase.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Dimens.md))
                    PrimaryButton(text = "Try again", onClick = viewModel::retry)
                }

                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    // Your words (live), then the assistant's streamed reply —
                    // both transcripts stay visible during the conversation.
                    if (state.liveTranscript.isNotBlank()) {
                        Text(
                            "“${state.liveTranscript}”",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Dimens.md))
                    } else if (state.lastUserText.isNotBlank() && state.aiTranscript.isBlank()) {
                        Text(
                            "“${state.lastUserText}”",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Dimens.md))
                    }
                    if (state.aiTranscript.isNotBlank()) {
                        Text(
                            state.aiTranscript,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (state.muted) {
                        Spacer(Modifier.height(Dimens.md))
                        Text(
                            "Microphone muted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Stop / restart control
        val active = state.phase is VoicePhase.Listening ||
            state.phase is VoicePhase.Thinking ||
            state.phase is VoicePhase.Speaking
        IconButton(
            onClick = {
                if (active) viewModel.stopConversation() else viewModel.retry()
            },
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
        ) {
            Icon(
                if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (active) "Stop listening" else "Start listening",
                tint = if (active) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(Dimens.lg))
    }
}

private fun phaseLabel(state: VoiceUiState): String = when (state.phase) {
    is VoicePhase.Idle -> "Paused"
    is VoicePhase.Listening -> "Listening…"
    is VoicePhase.Thinking -> "Thinking…"
    is VoicePhase.Speaking -> "…"
    is VoicePhase.Error -> "Hmm."
    is VoicePhase.PermissionNeeded -> "Microphone needed"
    is VoicePhase.PremiumNeeded -> "Premium feature"
}

/** Breathing orb whose rhythm follows the conversation phase. */
@Composable
private fun VoiceOrb(phase: VoicePhase) {
    val transition = rememberInfiniteTransition(label = "orb")
    val speed = when (phase) {
        is VoicePhase.Listening -> 900
        is VoicePhase.Thinking -> 450
        is VoicePhase.Speaking -> 650
        else -> 1600
    }
    val scale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(speed),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(180.dp)
                .scale(scale * 1.15f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )
        Box(
            Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        )
        Box(
            Modifier
                .size(100.dp)
                .scale(scale * 0.95f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
