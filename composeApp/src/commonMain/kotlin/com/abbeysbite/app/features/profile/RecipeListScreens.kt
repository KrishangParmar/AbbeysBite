package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.core.designsystem.StatusPill
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeStatus
import com.abbeysbite.app.data.repository.RecipeRepository
import com.abbeysbite.app.features.community.RecipeCard
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeListScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun MyRecipesScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onCreateRecipe: () -> Unit,
    recipeRepository: RecipeRepository = koinInject(),
) {
    var recipes by remember { mutableStateOf<List<Recipe>?>(null) }
    LaunchedEffect(Unit) {
        recipes = recipeRepository.myRecipes().getOrNull() ?: emptyList()
    }
    RecipeListScaffold("My recipes", onBack) { padding ->
        when {
            recipes == null -> LoadingList(padding)
            recipes!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Nothing shared yet",
                    message = "Publish your first hidden gem — even the simplest recipe helps someone.",
                    actionLabel = "Share a recipe",
                    onAction = onCreateRecipe,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
                modifier = Modifier.padding(padding),
            ) {
                items(recipes.orEmpty(), key = { it.id }) { recipe ->
                    Box {
                        RecipeCard(recipe = recipe, onOpen = { onOpenRecipe(recipe.id) })
                        if (recipe.status != RecipeStatus.PUBLISHED) {
                            StatusPill(
                                text = when (recipe.status) {
                                    RecipeStatus.DRAFT -> "Draft"
                                    RecipeStatus.UNDER_REVIEW -> "Under review"
                                    RecipeStatus.REMOVED -> "Removed"
                                    else -> ""
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Dimens.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedRecipesScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    recipeRepository: RecipeRepository = koinInject(),
) {
    var recipes by remember { mutableStateOf<List<Recipe>?>(null) }
    LaunchedEffect(Unit) {
        recipes = recipeRepository.savedRecipes().getOrNull() ?: emptyList()
    }
    RecipeListScaffold("Saved recipes", onBack) { padding ->
        when {
            recipes == null -> LoadingList(padding)
            recipes!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "No saved recipes",
                    message = "Tap the bookmark on any recipe to keep it here.",
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
                modifier = Modifier.padding(padding),
            ) {
                items(recipes.orEmpty(), key = { it.id }) { recipe ->
                    RecipeCard(recipe = recipe, onOpen = { onOpenRecipe(recipe.id) })
                }
            }
        }
    }
}

@Composable
private fun LoadingList(padding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.md),
        modifier = Modifier.padding(padding),
    ) {
        items(3) { SkeletonBox(Modifier.fillMaxWidth().height(280.dp)) }
    }
}
