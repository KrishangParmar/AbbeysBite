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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.LocalAppColors
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.core.designsystem.SectionHeader
import com.abbeysbite.app.core.designsystem.StatusPill
import com.abbeysbite.app.data.model.AdditionEffort
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.NutrientStatus
import com.abbeysbite.app.data.model.SuggestedAddition

@Composable
fun AnalysisResultView(
    state: ImproveUiState,
    analysis: MealAnalysis,
    viewModel: ImproveViewModel,
    onOpenChat: () -> Unit,
    onOpenVoice: () -> Unit,
) {
    val nourish = LocalAppColors.current
    var shareSheetOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Edge-to-edge meal image
        state.imageBytes?.let { bytes ->
            Image(
                painter = rememberAsyncImagePainter(bytes),
                contentDescription = "Photo of ${analysis.detectedMealName}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Crop,
            )
        }

        Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
            Spacer(Modifier.height(Dimens.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(analysis.detectedMealName, style = MaterialTheme.typography.headlineMedium)
                    if (analysis.confidence in 0.01..0.65) {
                        Text(
                            "Best guess — tap “Something wrong?” to fix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = viewModel::openCorrectionSheet) {
                    Text(Brand.Copy.somethingWrong)
                }
            }

            if (analysis.notes.isNotBlank()) {
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    analysis.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Dimens.lg))
            SectionHeader(Brand.Copy.yourPlate)
            Spacer(Modifier.height(Dimens.md))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md)) {
                    NutrientRow("Protein", analysis.proteinStatus, analysis.proteinSources, nourish.protein)
                    HorizontalSpacer()
                    NutrientRow("Fibre", analysis.fibreStatus, analysis.fibreSources, nourish.fibre)
                    HorizontalSpacer()
                    NutrientRow("Healthy fat", analysis.healthyFatStatus, analysis.healthyFatSources, nourish.healthyFat)
                }
            }

            if (analysis.suggestedAdditions.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.xl))
                SectionHeader(Brand.Copy.easyAdditions)
                Spacer(Modifier.height(Dimens.md))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    analysis.suggestedAdditions.forEach { addition ->
                        AdditionCard(addition) { viewModel.suggestionOpened(addition.name) }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.xl))

            // Contextual chat entry
            AppCard(Modifier.fillMaxWidth(), onClick = onOpenChat) {
                Row(
                    modifier = Modifier.padding(Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Dimens.md))
                    Column(Modifier.weight(1f)) {
                        Text("${Brand.Copy.useWhatIHave} →", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tell me what’s in your kitchen and I’ll work with it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onOpenVoice) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Talk about this meal",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.lg))

            if (state.loggedEntryId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = LocalAppColors.current.present,
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text("Added to your journal", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                PrimaryButton(
                    text = "Log this meal",
                    onClick = viewModel::openLogSheet,
                    loading = state.logging,
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                OutlinedButton(
                    onClick = { shareSheetOpen = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = viewModel::reset,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text("Scan another")
                }
            }
            Spacer(Modifier.height(Dimens.md))
            Text(
                Brand.Copy.estimateDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }

    if (state.correctionSheetOpen) {
        CorrectionSheet(
            analysis = analysis,
            onDismiss = viewModel::closeCorrectionSheet,
            onApply = viewModel::applyCorrection,
        )
    }
    if (state.logSheetOpen) {
        LogMealSheet(
            onDismiss = viewModel::closeLogSheet,
            onLog = viewModel::logMeal,
        )
    }
    if (shareSheetOpen) {
        ShareCardSheet(
            analysis = analysis,
            imageBytes = state.imageBytes,
            onDismiss = { shareSheetOpen = false },
        )
    }
}

@Composable
private fun HorizontalSpacer() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.md)
            .height(1.dp)
            .background(LocalAppColors.current.subtleOutline)
    )
}

@Composable
private fun NutrientRow(
    label: String,
    status: NutrientStatus,
    sources: List<String>,
    accent: androidx.compose.ui.graphics.Color,
) {
    val nourish = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (sources.isNotEmpty()) {
                Text(
                    sources.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val (text, container, content) = when (status) {
            NutrientStatus.PRESENT -> Triple("On the plate", nourish.presentContainer, nourish.present)
            NutrientStatus.COULD_ADD -> Triple("Could add", nourish.couldAddContainer, nourish.couldAdd)
            NutrientStatus.UNCERTAIN -> Triple("Not sure", nourish.uncertainContainer, nourish.uncertain)
        }
        StatusPill(text = text, containerColor = container, contentColor = content)
    }
}

@Composable
private fun AdditionCard(addition: SuggestedAddition, onOpen: () -> Unit) {
    val nourish = LocalAppColors.current
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    addition.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (addition.pantryMatch) {
                    StatusPill(
                        text = "In your pantry",
                        containerColor = nourish.presentContainer,
                        contentColor = nourish.present,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                addition.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val effortLabel = when (addition.effort) {
                    AdditionEffort.NONE -> "No prep"
                    AdditionEffort.LOW -> "1–2 min"
                    AdditionEffort.MEDIUM -> "Quick cook"
                }
                StatusPill(
                    text = effortLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                addition.optionalSubstitution?.let { sub ->
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
