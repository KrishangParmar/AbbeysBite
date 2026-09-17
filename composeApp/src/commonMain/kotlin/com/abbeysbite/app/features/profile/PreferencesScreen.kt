package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.model.CookingSetup
import com.abbeysbite.app.data.model.DietPreference
import com.abbeysbite.app.data.model.EatingGoal
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.repository.PreferencesRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject(),
) {
    var prefs by remember { mutableStateOf<UserPreferences?>(null) }
    var avoidInput by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        prefs = preferencesRepository.load().getOrNull() ?: UserPreferences()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Food preferences") },
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
        val current = prefs ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.screenPadding),
        ) {
            Text("Goals", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                EatingGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = goal in current.goals,
                        onClick = {
                            prefs = current.copy(
                                goals = if (goal in current.goals) current.goals - goal
                                else current.goals + goal
                            )
                            saved = false
                        },
                        label = { Text(goal.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            Text("Diet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                DietPreference.entries.forEach { diet ->
                    FilterChip(
                        selected = current.dietPreference == diet,
                        onClick = { prefs = current.copy(dietPreference = diet); saved = false },
                        label = { Text(diet.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            Text("Foods to avoid / allergies", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.xs))
            Text(
                Brand.Copy.allergenDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = avoidInput,
                    onValueChange = { avoidInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. peanuts") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.width(Dimens.sm))
                IconButton(
                    onClick = {
                        val food = avoidInput.trim()
                        if (food.isNotEmpty() && current.avoidFoods.none { it.equals(food, true) }) {
                            prefs = current.copy(avoidFoods = current.avoidFoods + food)
                            saved = false
                        }
                        avoidInput = ""
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add food to avoid")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                current.avoidFoods.forEach { food ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(food, style = MaterialTheme.typography.bodyLarge)
                        IconButton(
                            onClick = {
                                prefs = current.copy(avoidFoods = current.avoidFoods - food)
                                saved = false
                            },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $food",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            Text("Cooking setup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                CookingSetup.entries.forEach { setup ->
                    FilterChip(
                        selected = current.cookingSetup == setup,
                        onClick = { prefs = current.copy(cookingSetup = setup); saved = false },
                        label = { Text(setup.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            PrimaryButton(
                text = if (saved) "Saved ✓" else "Save preferences",
                onClick = {
                    saving = true
                    scope.launch {
                        preferencesRepository.save(current.copy(onboardingCompleted = true))
                        saving = false
                        saved = true
                    }
                },
                loading = saving,
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}
