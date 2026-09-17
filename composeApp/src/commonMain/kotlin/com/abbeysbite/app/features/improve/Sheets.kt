package com.abbeysbite.app.features.improve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealType

/** "Something wrong?" — rename meal, add missed items, remove wrong ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    analysis: MealAnalysis,
    onDismiss: () -> Unit,
    onApply: (MealCorrection) -> Unit,
) {
    var name by remember { mutableStateOf(analysis.detectedMealName) }
    var removed by remember { mutableStateOf(setOf<String>()) }
    var added by remember { mutableStateOf(listOf<String>()) }
    var addInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.xl)) {
            Text("Fix this analysis", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.lg))

            Text("Meal name", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Dimens.sm))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(Dimens.lg))
            Text("What’s on the plate", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Dimens.sm))
            Text(
                "Tap an item to remove it if we got it wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    analysis.components.forEach { component ->
                        val isRemoved = component.name in removed
                        InputChip(
                            selected = !isRemoved,
                            onClick = {
                                removed = if (isRemoved) removed - component.name else removed + component.name
                            },
                            label = {
                                Text(
                                    component.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (isRemoved) {
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    } else null,
                                )
                            },
                            trailingIcon = if (!isRemoved) {
                                {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove ${component.name}",
                                        modifier = Modifier.width(16.dp),
                                    )
                                }
                            } else null,
                        )
                    }
                    added.forEach { extra ->
                        InputChip(
                            selected = true,
                            onClick = { added = added - extra },
                            label = { Text(extra, style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $extra",
                                    modifier = Modifier.width(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addInput,
                    onValueChange = { addInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add something we missed") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.width(Dimens.sm))
                IconButton(
                    onClick = {
                        val item = addInput.trim()
                        if (item.isNotEmpty() && added.none { it.equals(item, true) }) {
                            added = added + item
                        }
                        addInput = ""
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add ingredient")
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            PrimaryButton(
                text = "Re-analyze",
                onClick = {
                    onApply(
                        MealCorrection(
                            renamedMeal = name.trim().takeIf {
                                it.isNotEmpty() && it != analysis.detectedMealName
                            },
                            addedComponents = added,
                            removedComponents = removed.toList(),
                        )
                    )
                },
            )
        }
    }
}

/** Pick which meal slot this belongs to before logging. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMealSheet(
    onDismiss: () -> Unit,
    onLog: (MealType) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.xl)) {
            Text("Add to journal as…", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                MealType.entries.forEach { type ->
                    AppCard(Modifier.fillMaxWidth(), onClick = { onLog(type) }) {
                        Text(
                            type.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(Dimens.md),
                        )
                    }
                }
            }
        }
    }
}
