package com.abbeysbite.app.features.improve

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.designsystem.EmptyState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImproveTab(
    onOpenChat: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    viewModel: ImproveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
        contentKey = { it::class.simpleName },
        label = "improvePhase",
    ) { phase ->
        when (phase) {
            is ImprovePhase.Input -> ImproveInput(state, viewModel)
            is ImprovePhase.Analyzing -> AnalyzingView(state, phase.statusText)
            is ImprovePhase.Result -> AnalysisResultView(
                state = state,
                analysis = phase.analysis,
                viewModel = viewModel,
                onOpenChat = onOpenChat,
                onOpenVoice = onOpenVoice,
            )
            is ImprovePhase.Failed -> FailedView(phase.error, viewModel)
            is ImprovePhase.LimitReached -> LimitReachedView(viewModel, onOpenPaywall)
        }
    }
}

// ---------------------------------------------------------------- input phase

@Composable
private fun ImproveInput(state: ImproveUiState, viewModel: ImproveViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.xl))
        Text(
            "A little more nourishment, no rules",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            Brand.Copy.improveHeading,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            Brand.Copy.improveSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.xl))

        if (!state.aiReady) {
            AiSetupCard(state.modelState, viewModel)
            Spacer(Modifier.height(Dimens.lg))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
            CaptureCard(
                modifier = Modifier.weight(1f),
                enabled = state.aiReady,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) },
                title = "Take a photo",
                onClick = viewModel::capturePhoto,
            )
            CaptureCard(
                modifier = Modifier.weight(1f),
                enabled = state.aiReady,
                icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) },
                title = "Choose a photo",
                onClick = viewModel::pickPhoto,
            )
        }

        Spacer(Modifier.height(Dimens.xl))
        Text(
            Brand.Copy.orTellMe,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.sm))
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. plain toast with butter") },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.analyzeDescription() }),
            enabled = state.aiReady,
            trailingIcon = {
                IconButton(
                    onClick = viewModel::analyzeDescription,
                    enabled = state.description.isNotBlank() && state.aiReady,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Analyze description")
                }
            },
        )

        state.analysesRemaining?.let { remaining ->
            Spacer(Modifier.height(Dimens.lg))
            Text(
                if (remaining > 0) "$remaining free ${if (remaining == 1) "analysis" else "analyses"} left today"
                else "You’ve used today’s free analyses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

@Composable
private fun CaptureCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
        AppCard(
            modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f },
            onClick = { if (enabled) onClick() },
        ) {
            Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = Dimens.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.height(Dimens.md))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.xs))
            Text(
                if (title.startsWith("Take")) "Use your camera" else "From your gallery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Your private AI" setup surface. Everything runs on-device — this card
 * walks the user through getting the model onto the phone without ever
 * exposing technical filenames.
 */
@Composable
private fun AiSetupCard(
    modelState: com.abbeysbite.app.ai.local.ModelState,
    viewModel: ImproveViewModel,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Dimens.sm))
                Text("Your private AI", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(Dimens.sm))
            when (modelState) {
                is com.abbeysbite.app.ai.local.ModelState.Checking -> {
                    Text(
                        "Checking your AI setup…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is com.abbeysbite.app.ai.local.ModelState.Downloading -> {
                    Text(
                        "Preparing your private AI… ${modelState.progressPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { modelState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    androidx.compose.material3.TextButton(onClick = viewModel::cancelModelDownload) {
                        Text("Pause")
                    }
                }
                is com.abbeysbite.app.ai.local.ModelState.Verifying -> {
                    Text(
                        "Verifying your private AI…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is com.abbeysbite.app.ai.local.ModelState.NotInstalled -> {
                    val canDownload =
                        modelState.source is com.abbeysbite.app.ai.local.ModelSource.Url
                    Text(
                        if (canDownload) {
                            "Meal analysis runs entirely on your phone — nothing you eat ever leaves it. " +
                                "One-time setup downloads the AI (about " +
                                com.abbeysbite.app.ai.local.GemmaModelSpec.DISPLAY_SIZE +
                                ", Wi-Fi recommended)."
                        } else {
                            "This build needs its on-device AI provisioned by a developer. " +
                                "Nothing you eat ever leaves your phone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!modelState.enoughSpace && canDownload) {
                        Spacer(Modifier.height(Dimens.xs))
                        Text(
                            "Free up some space first — about ${com.abbeysbite.app.ai.local.GemmaModelSpec.DISPLAY_SIZE} is needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    Row {
                        if (canDownload) {
                            androidx.compose.material3.Button(
                                onClick = viewModel::startModelDownload,
                                enabled = modelState.enoughSpace,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Set up my AI") }
                            Spacer(Modifier.width(Dimens.sm))
                        }
                        androidx.compose.material3.TextButton(onClick = viewModel::retryModelCheck) {
                            Text("Check again")
                        }
                    }
                }
                is com.abbeysbite.app.ai.local.ModelState.Failed -> {
                    Text(
                        modelState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    Row {
                        androidx.compose.material3.TextButton(onClick = viewModel::retryModelCheck) {
                            Text("Try again")
                        }
                        if (modelState.reason == com.abbeysbite.app.ai.local.ModelFailure.CORRUPT) {
                            androidx.compose.material3.TextButton(onClick = {
                                viewModel.retryModelCheck()
                            }) { Text("Re-check file") }
                        }
                    }
                }
                is com.abbeysbite.app.ai.local.ModelState.Installed -> Unit
            }
        }
    }
}

// ------------------------------------------------------------ analyzing phase

@Composable
private fun AnalyzingView(state: ImproveUiState, statusText: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Dimens.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(Dimens.cardRadius)),
        ) {
            val bytes = state.imageBytes
            if (bytes != null) {
                Image(
                    painter = rememberAsyncImagePainter(bytes),
                    contentDescription = "Your meal photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
            ScanLine()
        }
        Spacer(Modifier.height(Dimens.xl))
        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "statusCopy",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Soft sweeping highlight over the photo while analyzing. */
@Composable
private fun ScanLine() {
    val transition = rememberInfiniteTransition(label = "scan")
    val offset by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanOffset",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.9f },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .graphicsLayer { translationY = offset * 320.dp.toPx() }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        )
    }
}

// --------------------------------------------------------------- error phases

@Composable
private fun FailedView(error: AppError, viewModel: ImproveViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "That didn’t work",
            message = error.message,
            actionLabel = "Try again",
            onAction = viewModel::retry,
        )
    }
}

@Composable
private fun LimitReachedView(viewModel: ImproveViewModel, onOpenPaywall: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                title = "You’ve used today’s free analyses",
                message = "Come back tomorrow, or go unlimited with Premium — unlimited analyses, chat and voice.",
                actionLabel = "See Premium",
                onAction = onOpenPaywall,
            )
            androidx.compose.material3.TextButton(onClick = viewModel::reset) {
                Text("Maybe later")
            }
        }
    }
}
