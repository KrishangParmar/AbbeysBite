package com.abbeysbite.app.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.model.CookingSetup
import com.abbeysbite.app.data.model.DietPreference
import com.abbeysbite.app.data.model.EatingGoal
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isLast = state.step == OnboardingUiState.STEP_COUNT - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(current = state.step, total = OnboardingUiState.STEP_COUNT)
            TextButton(onClick = { viewModel.finish(onFinished) }) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimens.lg))

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "onboardingStep",
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> GoalsStep(state, viewModel)
                    1 -> DietStep(state, viewModel)
                    2 -> AvoidStep(state, viewModel)
                    3 -> CookingStep(state, viewModel)
                    4 -> PantryStep(state, viewModel)
                }
                Spacer(Modifier.height(Dimens.xl))
            }
        }

        state.saveError?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.sm),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.md),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step > 0) {
                TextButton(onClick = viewModel::back) { Text("Back") }
            }
            Box(Modifier.weight(1f)) {
                PrimaryButton(
                    text = if (isLast) "Let’s go" else "Continue",
                    onClick = { if (isLast) viewModel.finish(onFinished) else viewModel.next() },
                    loading = state.saving,
                )
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (i == current) 22.dp else 8.dp)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) {
        Spacer(Modifier.height(Dimens.sm))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Dimens.lg))
}

@Composable
private fun SelectableChipColumn(
    options: List<Pair<String, Boolean>>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        options.forEachIndexed { index, (label, selected) ->
            FilterChip(
                selected = selected,
                onClick = { onToggle(index) },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun GoalsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading("What would make eating easier for you?", "Pick as many as you like.")
    val goals = EatingGoal.entries
    SelectableChipColumn(
        options = goals.map { it.label to (it in state.goals) },
        onToggle = { viewModel.toggleGoal(goals[it]) },
    )
}

@Composable
private fun DietStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading("How do you like to eat?")
    val diets = DietPreference.entries
    SelectableChipColumn(
        options = diets.map { it.label to (it == state.dietPreference) },
        onToggle = { viewModel.selectDiet(diets[it]) },
    )
}

@Composable
private fun AvoidStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "Anything you avoid?",
        "Allergies, intolerances, or foods you just don’t enjoy.",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.avoidInput,
            onValueChange = viewModel::onAvoidInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("e.g. peanuts") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.width(Dimens.sm))
        IconButton(onClick = viewModel::addAvoidFood) {
            Icon(Icons.Filled.Add, contentDescription = "Add food to avoid")
        }
    }
    Spacer(Modifier.height(Dimens.md))
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        state.avoidFoods.forEach { food ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(food, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { viewModel.removeAvoidFood(food) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $food",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Dimens.md))
    Text(
        Brand.Copy.allergenDisclaimer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CookingStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading("What’s your cooking setup?")
    val setups = CookingSetup.entries
    SelectableChipColumn(
        options = setups.map { it.label to (it == state.cookingSetup) },
        onToggle = { viewModel.selectCookingSetup(setups[it]) },
    )
}

@Composable
private fun PantryStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "What do you usually have around?",
        "Optional — helps us suggest additions you can actually make. You can edit this anytime.",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.pantryInput,
            onValueChange = viewModel::onPantryInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("e.g. eggs, peanut butter, oats") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.width(Dimens.sm))
        IconButton(onClick = viewModel::addPantryItem) {
            Icon(Icons.Filled.Add, contentDescription = "Add pantry item")
        }
    }
    Spacer(Modifier.height(Dimens.md))
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        state.pantryItems.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { viewModel.removePantryItem(item) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
