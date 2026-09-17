package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.data.repository.PantryRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    onBack: () -> Unit,
    pantryRepository: PantryRepository = koinInject(),
) {
    val items by pantryRepository.items.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) { pantryRepository.load() }

    fun add() {
        val name = input.trim()
        if (name.isEmpty()) return
        scope.launch {
            pantryRepository.add(name)
                .onSuccess { input = ""; error = null }
                .onFailure { error = it.message }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pantry") },
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
                .imePadding()
                .padding(horizontal = Dimens.screenPadding),
        ) {
            Text(
                "What you usually have around. The kitchen assistant leans on this to suggest additions you can actually make.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = Dimens.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. peanut butter") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                Spacer(Modifier.width(Dimens.sm))
                IconButton(onClick = ::add) {
                    Icon(Icons.Filled.Add, contentDescription = "Add pantry item")
                }
            }
            Spacer(Modifier.padding(top = Dimens.md))
            if (items.isEmpty()) {
                EmptyState(
                    title = "Pantry’s empty",
                    message = "Add a few staples — eggs, oats, peanut butter — and suggestions get smarter.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = Dimens.xl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                ) {
                    items(items, key = { it.id }) { item ->
                        AppCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = Dimens.md, vertical = Dimens.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { scope.launch { pantryRepository.remove(item.id) } }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove ${item.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
