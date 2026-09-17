package com.abbeysbite.app.features.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.abbeysbite.app.data.model.FeedSection
import com.abbeysbite.app.data.model.Recipe
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CommunityTab(
    onOpenRecipe: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCreateRecipe: () -> Unit = {},
    viewModel: CommunityViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRecipe,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Share a recipe")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hidden Gems", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Real recipes from real kitchens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search recipes")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                items(FeedSection.entries) { section ->
                    FilterChip(
                        selected = section == state.section,
                        onClick = { viewModel.selectSection(section) },
                        label = { Text(section.label) },
                    )
                }
            }

            Spacer(Modifier.height(Dimens.sm))

            when {
                state.loading -> LazyColumn(
                    contentPadding = PaddingValues(Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    items(3) { SkeletonBox(Modifier.fillMaxWidth().height(300.dp)) }
                }

                state.error != null -> EmptyState(
                    title = "Couldn’t load recipes",
                    message = state.error?.message.orEmpty(),
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )

                state.recipes.isEmpty() -> EmptyState(
                    title = "No recipes here yet",
                    message = "Be the first to share something delicious.",
                    actionLabel = "Share a recipe",
                    onAction = onCreateRecipe,
                )

                else -> {
                    // Free users see one tasteful sponsored card per ~6 recipes
                    // (Community is the ONLY ad surface; premium sees none).
                    val adsManager = org.koin.compose.koinInject<com.abbeysbite.app.ads.AdsManager>()
                    val adsOn by adsManager.adsEnabled.collectAsState()
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            top = Dimens.sm,
                            bottom = 90.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.lg),
                    ) {
                        state.recipes.forEachIndexed { index, recipe ->
                            item(key = recipe.id) {
                                RecipeCard(
                                    recipe = recipe,
                                    onOpen = { onOpenRecipe(recipe.id) },
                                )
                            }
                            if (adsOn && index > 0 && index % 6 == 5) {
                                item(key = "ad-$index") {
                                    com.abbeysbite.app.ads.CommunityNativeAdCard(Modifier)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeCard(recipe: Recipe, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = Dimens.cardRadius, topEnd = Dimens.cardRadius)),
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
            Column(Modifier.padding(Dimens.md)) {
                Text(recipe.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${recipe.totalMinutes} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        recipe.tags.take(2).forEach { tag ->
                            StatusPill(
                                text = tag,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        // Feed items do not carry the current user's like
                        // state. A filled heart here falsely suggests that
                        // the signed-in user liked every popular recipe.
                        Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        recipe.likeCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Soft generated cover for recipes without a photo (demo data, drafts). */
@Composable
fun RecipePlaceholderArt(title: String) {
    val palettes = listOf(
        Pair(0xFFDCEDE4, 0xFF2E7D5B), Pair(0xFFE4EBF5, 0xFF5B7DB1),
        Pair(0xFFF6EDD8, 0xFFC79A3B), Pair(0xFFE9EFDF, 0xFF7A9B57),
    )
    val (bg, fg) = palettes[title.hashCode().mod(palettes.size)]
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(bg)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""),
            style = MaterialTheme.typography.displayMedium,
            color = androidx.compose.ui.graphics.Color(fg).copy(alpha = 0.55f),
        )
    }
}
