package com.abbeysbite.app.features.improve

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.ai.ChatRole
import com.abbeysbite.app.core.designsystem.Dimens
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealChatScreen(
    onBack: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: MealChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val analysis by viewModel.session.analysis.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, state.sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    // The chat and voice screens share one platform recognizer — leaving this
    // screen mid-dictation must release the mic, or the stale dictation
    // collector eats the voice screen's speech events.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopDictation() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        viewModel.session.imageBytes?.let { bytes ->
                            Image(
                                painter = rememberAsyncImagePainter(bytes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(Dimens.sm))
                        }
                        Text(
                            analysis?.detectedMealName ?: "Your meal",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVoice) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Switch to voice mode",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                if (messages.isEmpty()) {
                    item {
                        SuggestedPrompts(onPick = { viewModel.send(it) })
                    }
                }
                items(messages) { message ->
                    MessageBubble(
                        text = message.content,
                        isUser = message.role == ChatRole.USER,
                    )
                }
                if (state.sending) {
                    item {
                        val streaming = state.streamingReply
                        if (!streaming.isNullOrBlank()) {
                            // Live-streaming assistant bubble.
                            MessageBubble(text = streaming, isUser = false)
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Dimens.sm),
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = Dimens.xs),
                        )
                    }
                }
            }

            if (state.limitReached) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Dimens.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "You’ve used today’s free chat messages.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row {
                        TextButton(onClick = viewModel::dismissLimit) { Text("Later") }
                        TextButton(onClick = onOpenPaywall) { Text("Go unlimited") }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.md, vertical = Dimens.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (state.dictating) "Listening…" else "I only have eggs and cheese…")
                    },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.width(Dimens.xs))
                IconButton(onClick = viewModel::toggleDictation) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (state.dictating) "Stop dictation" else "Dictate message",
                        tint = if (state.dictating) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sending) {
                    IconButton(onClick = viewModel::stopStreaming) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop response",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.send() },
                        enabled = state.input.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (state.input.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp,
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SuggestedPrompts(onPick: (String) -> Unit) {
    val prompts = listOf(
        "I only have eggs and cheese",
        "What could make this more filling?",
        "I have 5 minutes",
        "I’m in a hostel",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        Text(
            "Ask me anything about this meal — I know what’s on your plate and in your pantry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Dimens.xs))
        prompts.forEach { prompt ->
            androidx.compose.material3.SuggestionChip(
                onClick = { onPick(prompt) },
                label = { Text(prompt) },
            )
        }
    }
}
