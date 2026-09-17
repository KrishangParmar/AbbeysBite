package com.abbeysbite.app.features.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.LocalAppColors
import com.abbeysbite.app.core.designsystem.SectionHeader
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.MealType
import com.abbeysbite.app.data.model.NutrientStatus
import com.abbeysbite.app.data.model.RhythmDay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JournalTab(viewModel: JournalViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.lg))
        Text("Journal", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Dimens.lg))

        when {
            state.loading -> JournalSkeleton()
            state.error != null -> EmptyState(
                title = "Couldn’t load your journal",
                message = state.error?.message.orEmpty(),
                actionLabel = "Try again",
                onAction = viewModel::load,
            )
            else -> JournalContent(state, viewModel)
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

@Composable
private fun JournalSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
        SkeletonBox(Modifier.fillMaxWidth().height(90.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(140.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(200.dp))
    }
}

@Composable
private fun JournalContent(state: JournalUiState, viewModel: JournalViewModel) {
    // ------------------------------------------------ rhythm
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.md)) {
            SectionHeader(Brand.Copy.yourRhythm)
            Spacer(Modifier.height(Dimens.md))
            RhythmStrip(
                week = state.week,
                selected = state.selectedDate,
                onSelect = viewModel::selectDate,
            )
            Spacer(Modifier.height(Dimens.md))
            Text(
                when (state.nourishingDays) {
                    0 -> "A fresh week ahead"
                    1 -> "1 nourishing day this week"
                    else -> "${state.nourishingDays} nourishing days this week"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.streak >= 2) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${state.streak}-day logging streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(Dimens.lg))

    // ------------------------------------------------ selected day meals
    val meals = state.entriesForSelectedDay
    val orderedTypes = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
    if (meals.isEmpty()) {
        AppCard(Modifier.fillMaxWidth()) {
            EmptyState(
                title = "Nothing logged this day",
                message = "Scan a meal in the Improve tab and tap “Log this meal” to see it here.",
            )
        }
    } else {
        orderedTypes.forEach { type ->
            meals[type]?.let { entries ->
                Text(
                    type.label,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(Dimens.sm))
                entries.forEach { entry ->
                    MealEntryCard(entry, onDelete = { viewModel.deleteEntry(entry.id) })
                    Spacer(Modifier.height(Dimens.sm))
                }
                Spacer(Modifier.height(Dimens.md))
            }
        }
    }

    Spacer(Modifier.height(Dimens.md))

    // ------------------------------------------------ snapshot
    SectionHeader("This week’s nourishment")
    Spacer(Modifier.height(Dimens.md))
    SnapshotCard(state)

    // ------------------------------------------------ weekly insight
    if (state.weeklyInsight != null || state.insightLoading) {
        Spacer(Modifier.height(Dimens.lg))
        SectionHeader("Weekly reflection")
        Spacer(Modifier.height(Dimens.md))
        if (state.insightLoading) {
            SkeletonBox(Modifier.fillMaxWidth().height(120.dp))
        } else {
            state.weeklyInsight?.let { insight ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        insight.observations.forEach { obs ->
                            Row {
                                Text("•  ", style = MaterialTheme.typography.bodyLarge)
                                Text(obs, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (insight.ideas.isNotEmpty()) {
                            Spacer(Modifier.height(Dimens.xs))
                            Text(
                                "Worth a try",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            insight.ideas.forEach { idea ->
                                Row {
                                    Text("→  ", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(idea, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RhythmStrip(
    week: List<RhythmDay>,
    selected: kotlinx.datetime.LocalDate?,
    onSelect: (kotlinx.datetime.LocalDate) -> Unit,
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        week.forEachIndexed { index, day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(day.date) }
                    .padding(6.dp),
            ) {
                Text(
                    labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                day.logged -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .let {
                            if (day.date == selected) {
                                it.background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (day.logged) 1f else 0.25f
                                    ),
                                    CircleShape,
                                )
                            } else it
                        },
                )
            }
        }
    }
}

@Composable
private fun MealEntryCard(entry: MealEntry, onDelete: () -> Unit) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val nourish = LocalAppColors.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.mealName.ifBlank { entry.analysis?.detectedMealName ?: "Meal" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (confirmingDelete) {
                    androidx.compose.material3.TextButton(onClick = onDelete) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                    androidx.compose.material3.TextButton(onClick = { confirmingDelete = false }) {
                        Text("Keep")
                    }
                } else {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            entry.analysis?.let { analysis ->
                Spacer(Modifier.height(Dimens.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    NutrientDot("Protein", analysis.proteinStatus, nourish.protein)
                    NutrientDot("Fibre", analysis.fibreStatus, nourish.fibre)
                    NutrientDot("Healthy fat", analysis.healthyFatStatus, nourish.healthyFat)
                }
            }
        }
    }
}

@Composable
private fun NutrientDot(label: String, status: NutrientStatus, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        NutrientStatus.PRESENT -> color
                        NutrientStatus.COULD_ADD -> color.copy(alpha = 0.25f)
                        NutrientStatus.UNCERTAIN -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SnapshotCard(state: JournalUiState) {
    val nourish = LocalAppColors.current
    val snapshot = state.snapshot
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            SnapshotRow("Meals logged", snapshot.mealsLogged.toString())
            SnapshotRow("Days with meals", snapshot.daysWithMeals.toString())
            SnapshotRow(
                "Protein on the plate",
                "${snapshot.proteinPresentCount} of ${snapshot.mealsLogged} meals",
                nourish.protein,
            )
            SnapshotRow(
                "Fibre on the plate",
                "${snapshot.fibrePresentCount} of ${snapshot.mealsLogged} meals",
                nourish.fibre,
            )
            SnapshotRow(
                "Healthy fats on the plate",
                "${snapshot.healthyFatPresentCount} of ${snapshot.mealsLogged} meals",
                nourish.healthyFat,
            )
            SnapshotRow("Plant variety", "≈ ${snapshot.plantVariety} different plants")
        }
    }
}

@Composable
private fun SnapshotRow(label: String, value: String, dotColor: androidx.compose.ui.graphics.Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        dotColor?.let {
            Box(Modifier.size(8.dp).clip(CircleShape).background(it))
            Spacer(Modifier.width(Dimens.sm))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
