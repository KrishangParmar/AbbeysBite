package com.abbeysbite.app.features.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.core.designsystem.StatusPill
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.purchased) {
        if (state.purchased) onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Dimens.md), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Dimens.md))
        Text(
            "${Brand.appName} Premium",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            "Your kitchen assistant, fully unlocked",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(Dimens.lg))
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                listOf(
                    "Unlimited meal analyses",
                    "Unlimited contextual chat",
                    "Voice conversations",
                    "Pantry-aware suggestions",
                    "Deeper weekly insights",
                    "Personalized preference memory",
                    "Advanced recipe search",
                ).forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Dimens.sm))
                        Text(feature, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.lg))

        when {
            state.loading -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                SkeletonBox(Modifier.fillMaxWidth().height(76.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(76.dp))
            }

            state.unavailable -> EmptyState(
                title = "Purchases unavailable right now",
                message = "The store can’t be reached at the moment. Everything free stays free — try again later.",
                actionLabel = "Retry",
                onAction = viewModel::load,
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                state.packages.forEachIndexed { index, pkg ->
                    val selected = index == state.selectedIndex
                    AppCard(
                        Modifier
                            .fillMaxWidth()
                            .border(
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                shape = RoundedCornerShape(Dimens.cardRadius),
                            ),
                        onClick = { viewModel.select(index) },
                    ) {
                        Row(
                            Modifier.padding(Dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pkg.title, style = MaterialTheme.typography.titleMedium)
                                    if (pkg.hasFreeTrial) {
                                        Spacer(Modifier.width(Dimens.sm))
                                        StatusPill(
                                            "Free trial",
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Text(
                                    "${pkg.priceLabel} ${pkg.periodLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = { viewModel.select(index) },
                            )
                        }
                    }
                }

                state.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(Dimens.sm))
                PrimaryButton(
                    text = "Continue",
                    onClick = viewModel::purchase,
                    loading = state.purchasing,
                    enabled = state.packages.isNotEmpty(),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::restore, enabled = !state.restoring) {
                        Text(if (state.restoring) "Restoring…" else "Restore purchases")
                    }
                }
                Text(
                    "Recurring billing, cancel anytime in your store account settings. " +
                        "Payment is charged to your App Store / Google Play account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}
