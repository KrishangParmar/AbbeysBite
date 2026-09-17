package com.abbeysbite.app.features.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.InitialsAvatar
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.core.designsystem.StatusPill
import com.abbeysbite.app.domain.Validators
import com.abbeysbite.app.platform.PlatformYouTubePlayer
import com.abbeysbite.app.platform.UrlOpener
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RecipeDetailViewModel = koinViewModel(),
    urlOpener: UrlOpener = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    LaunchedEffect(state.ownerActionDone) { if (state.ownerActionDone) onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::share) {
                        Icon(Icons.Filled.Share, contentDescription = "Share recipe")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (state.isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { menuOpen = false; onEdit(recipeId) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Unpublish") },
                                    onClick = { menuOpen = false; viewModel.unpublish() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; viewModel.delete() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Report recipe") },
                                    onClick = { menuOpen = false; viewModel.openReportSheet() },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(
                Modifier.padding(padding).padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                SkeletonBox(Modifier.fillMaxWidth().height(220.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(30.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(180.dp))
            }

            state.error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Recipe unavailable",
                    message = state.error?.message ?: "This recipe may have been removed.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }

            else -> state.detail?.let { detail ->
                RecipeDetailContent(
                    state = state,
                    detail = detail,
                    viewModel = viewModel,
                    urlOpener = urlOpener,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (state.reportSheetOpen) {
        ReportSheet(
            onDismiss = viewModel::closeReportSheet,
            onReport = viewModel::reportRecipe,
            onBlockAuthor = viewModel::blockAuthor,
        )
    }
}

@Composable
private fun RecipeDetailContent(
    state: RecipeDetailUiState,
    detail: com.abbeysbite.app.data.model.RecipeDetail,
    viewModel: RecipeDetailViewModel,
    urlOpener: UrlOpener,
    modifier: Modifier = Modifier,
) {
    val recipe = detail.recipe
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            if (recipe.coverImageUrl != null && !recipe.coverImageUrl.startsWith("memory://")) {
                AsyncImage(
                    model = recipe.coverImageUrl,
                    contentDescription = "Photo of ${recipe.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                RecipePlaceholderArt(recipe.title)
            }
        }

        Column(Modifier.padding(Dimens.screenPadding)) {
            Text(recipe.title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(Dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(name = detail.author?.displayOrUsername ?: "?", size = 30.dp)
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    detail.author?.displayOrUsername ?: "Community member",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::toggleLike) {
                    Icon(
                        if (state.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (state.liked) "Unlike" else "Like",
                        tint = if (state.liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(state.likeCount.toString(), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(Dimens.xs))
                IconButton(onClick = viewModel::toggleSave) {
                    Icon(
                        if (state.saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (state.saved) "Remove from saved" else "Save recipe",
                        tint = if (state.saved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recipe.description.isNotBlank()) {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    recipe.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Dimens.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                StatusPill(
                    "Prep ${recipe.prepMinutes} min",
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    "Cook ${recipe.cookMinutes} min",
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    recipe.difficulty.label,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    "Serves ${recipe.servings}",
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recipe.tags.isNotEmpty() || recipe.dietTags.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    (recipe.dietTags + recipe.tags).take(5).forEach { tag ->
                        StatusPill(
                            tag,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // ------------------------------------------ video
            val videoId = Validators.youtubeVideoId(recipe.youtubeUrl)
            if (videoId != null) {
                Spacer(Modifier.height(Dimens.lg))
                Text("Watch it made", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Dimens.md))
                PlatformYouTubePlayer(
                    videoId = videoId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp)),
                )
                TextButton(onClick = { urlOpener.openUrl("https://www.youtube.com/watch?v=$videoId") }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in YouTube")
                }
            }

            // ------------------------------------------ ingredients
            Spacer(Modifier.height(Dimens.lg))
            Text("Ingredients", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.md))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    detail.ingredients.forEach { ingredient ->
                        Row {
                            Text(
                                buildString {
                                    ingredient.quantity?.let { append(it); append(" ") }
                                    ingredient.unit?.let { append(it); append(" ") }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                                ingredient.substitution?.let { sub ->
                                    Text(
                                        "or: $sub",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------ steps
            Spacer(Modifier.height(Dimens.lg))
            Text("Steps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.md))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                detail.steps.forEach { step ->
                    Row {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .padding(0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                step.stepNumber.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            step.instruction,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            recipe.tips?.takeIf { it.isNotBlank() }?.let { tips ->
                Spacer(Modifier.height(Dimens.lg))
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.md)) {
                        Text("Tips", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.xs))
                        Text(tips, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.reportSubmitted) {
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Thanks — our team will take a look.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    onDismiss: () -> Unit,
    onReport: (reason: String, details: String?) -> Unit,
    onBlockAuthor: (() -> Unit)? = null,
) {
    var details by remember { mutableStateOf("") }
    val reasons = listOf(
        "Spam or misleading",
        "Inappropriate content",
        "Unsafe food advice",
        "Stolen content",
        "Something else",
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.xl)) {
            Text("Report", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.md))
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Anything we should know? (optional)") },
                shape = MaterialTheme.shapes.medium,
                maxLines = 3,
            )
            Spacer(Modifier.height(Dimens.md))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                reasons.forEach { reason ->
                    AppCard(
                        Modifier.fillMaxWidth(),
                        onClick = { onReport(reason, details.trim().ifBlank { null }) },
                    ) {
                        Text(
                            reason,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(Dimens.md),
                        )
                    }
                }
            }
            onBlockAuthor?.let { block ->
                Spacer(Modifier.height(Dimens.sm))
                TextButton(onClick = block) {
                    Text("Block this creator", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
