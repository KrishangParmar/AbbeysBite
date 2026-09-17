package com.abbeysbite.app.features.community

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.model.RecipeDifficulty
import org.koin.compose.viewmodel.koinViewModel

private val CUISINES = listOf("Indian", "Asian", "Mediterranean", "Mexican", "Italian", "Global")
private val TAG_OPTIONS = listOf("quick", "budget", "breakfast", "lunch", "dinner", "snack", "no-cook", "microwave", "hostel", "meal-prep", "spicy", "leftovers")
private val DIET_OPTIONS = listOf("vegetarian", "vegan", "gluten-free", "dairy-free")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    recipeId: String?,
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
    viewModel: RecipeEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val draft = state.draft

    LaunchedEffect(recipeId) { viewModel.initialize(recipeId) }
    LaunchedEffect(state.published) { state.published?.let { onPublished(it.id) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == null) "Share a recipe" else "Edit recipe") },
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.screenPadding),
        ) {
            // ------------------------------------------------ cover photo
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(Dimens.cardRadius)),
            ) {
                when {
                    state.coverImageBytes != null -> Image(
                        painter = rememberAsyncImagePainter(state.coverImageBytes),
                        contentDescription = "Cover photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    state.existingCoverUrl != null && !state.existingCoverUrl!!.startsWith("memory://") ->
                        coil3.compose.AsyncImage(
                            model = state.existingCoverUrl,
                            contentDescription = "Cover photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    else -> RecipePlaceholderArt(draft.title.ifBlank { "New recipe" })
                }
            }
            Spacer(Modifier.height(Dimens.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                OutlinedButton(onClick = viewModel::captureCoverImage, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = viewModel::pickCoverImage, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(Dimens.md))
            OutlinedTextField(
                value = draft.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Short description") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(Dimens.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                OutlinedTextField(
                    value = draft.prepMinutes,
                    onValueChange = { v -> viewModel.update { it.copy(prepMinutes = v.filter(Char::isDigit).take(3)) } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Prep min") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = draft.cookMinutes,
                    onValueChange = { v -> viewModel.update { it.copy(cookMinutes = v.filter(Char::isDigit).take(3)) } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Cook min") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = draft.servings,
                    onValueChange = { v -> viewModel.update { it.copy(servings = v.filter(Char::isDigit).take(2)) } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Serves") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Spacer(Modifier.height(Dimens.md))
            Text("Difficulty", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Dimens.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                RecipeDifficulty.entries.forEach { diff ->
                    FilterChip(
                        selected = draft.difficulty == diff,
                        onClick = { viewModel.update { it.copy(difficulty = diff) } },
                        label = { Text(diff.label) },
                    )
                }
            }

            Spacer(Modifier.height(Dimens.md))
            Text("Cuisine", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Dimens.xs))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                CUISINES.forEach { cuisine ->
                    FilterChip(
                        selected = draft.cuisine == cuisine,
                        onClick = {
                            viewModel.update {
                                it.copy(cuisine = if (it.cuisine == cuisine) "" else cuisine)
                            }
                        },
                        label = { Text(cuisine) },
                    )
                }
            }

            Spacer(Modifier.height(Dimens.md))
            ChipMultiSelect(
                title = "Tags",
                options = TAG_OPTIONS,
                selected = draft.tags,
                onToggle = { tag ->
                    viewModel.update {
                        it.copy(tags = if (tag in it.tags) it.tags - tag else (it.tags + tag).take(8))
                    }
                },
            )
            Spacer(Modifier.height(Dimens.md))
            ChipMultiSelect(
                title = "Diet",
                options = DIET_OPTIONS,
                selected = draft.dietTags,
                onToggle = { tag ->
                    viewModel.update {
                        it.copy(dietTags = if (tag in it.dietTags) it.dietTags - tag else it.dietTags + tag)
                    }
                },
            )

            // ------------------------------------------------ ingredients
            Spacer(Modifier.height(Dimens.lg))
            Text("Ingredients", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.sm))
            draft.ingredients.forEachIndexed { index, ingredient ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ingredient.quantity,
                        onValueChange = { v ->
                            viewModel.update {
                                it.copy(ingredients = it.ingredients.replaceAt(index) { ing -> ing.copy(quantity = v.take(8)) })
                            }
                        },
                        modifier = Modifier.width(72.dp),
                        label = { Text("Qty") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.width(Dimens.xs))
                    OutlinedTextField(
                        value = ingredient.unit,
                        onValueChange = { v ->
                            viewModel.update {
                                it.copy(ingredients = it.ingredients.replaceAt(index) { ing -> ing.copy(unit = v.take(10)) })
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        label = { Text("Unit") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.width(Dimens.xs))
                    OutlinedTextField(
                        value = ingredient.name,
                        onValueChange = { v ->
                            viewModel.update {
                                it.copy(ingredients = it.ingredients.replaceAt(index) { ing -> ing.copy(name = v) })
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Ingredient") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    IconButton(
                        onClick = {
                            viewModel.update {
                                it.copy(ingredients = it.ingredients.filterIndexed { i, _ -> i != index }
                                    .ifEmpty { listOf(EditorIngredient()) })
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ingredient")
                    }
                }
                Spacer(Modifier.height(Dimens.xs))
            }
            OutlinedButton(
                onClick = { viewModel.update { it.copy(ingredients = it.ingredients + EditorIngredient()) } },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient")
            }

            // ------------------------------------------------ steps
            Spacer(Modifier.height(Dimens.lg))
            Text("Steps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.sm))
            draft.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    OutlinedTextField(
                        value = step,
                        onValueChange = { v ->
                            viewModel.update {
                                it.copy(steps = it.steps.mapIndexed { i, s -> if (i == index) v else s })
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Describe this step") },
                        minLines = 1,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.medium,
                    )
                    IconButton(
                        onClick = {
                            viewModel.update {
                                it.copy(steps = it.steps.filterIndexed { i, _ -> i != index }
                                    .ifEmpty { listOf("") })
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove step")
                    }
                }
                Spacer(Modifier.height(Dimens.xs))
            }
            OutlinedButton(
                onClick = { viewModel.update { it.copy(steps = it.steps + "") } },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add step")
            }

            // ------------------------------------------------ extras
            Spacer(Modifier.height(Dimens.lg))
            OutlinedTextField(
                value = draft.youtubeUrl,
                onValueChange = { v -> viewModel.update { it.copy(youtubeUrl = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube video URL (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(Dimens.md))
            OutlinedTextField(
                value = draft.tips,
                onValueChange = { v -> viewModel.update { it.copy(tips = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tips (optional)") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )

            if (state.errors.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.md))
                state.errors.forEach { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            PrimaryButton(
                text = if (recipeId == null) "Publish recipe" else "Save changes",
                onClick = viewModel::publish,
                loading = state.publishing,
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }

@Composable
private fun ChipMultiSelect(
    title: String,
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(Dimens.xs))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(option) },
            )
        }
    }
}

