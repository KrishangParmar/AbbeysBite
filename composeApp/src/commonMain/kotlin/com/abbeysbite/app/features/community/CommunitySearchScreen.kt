package com.abbeysbite.app.features.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class CommunitySearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Recipe>? = null,
    val error: AppError? = null,
    /** True when the last search used AI intent parsing. */
    val usedSmartSearch: Boolean = false,
)

class CommunitySearchViewModel(
    private val recipeRepository: RecipeRepository,
    private val aiProvider: AiProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CommunitySearchUiState())
    val state: StateFlow<CommunitySearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
        // Debounced plain keyword search while typing.
        searchJob?.cancel()
        if (value.trim().length < 3) {
            _state.update { it.copy(results = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runKeywordSearch(value)
        }
    }

    /**
     * Natural-language search ("something spicy with paneer in 15 minutes"):
     * AI translates the query into structured intent, falling back to keyword
     * matching when the parse fails.
     */
    fun smartSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            val intent = aiProvider.parseRecipeQuery(query).getOrNull()
            val result = if (intent != null) {
                recipeRepository.searchByIntent(intent)
            } else {
                recipeRepository.search(query)
            }
            result
                .onSuccess { recipes ->
                    _state.update {
                        it.copy(searching = false, results = recipes, usedSmartSearch = intent != null)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(searching = false, error = error) }
                }
        }
    }

    private suspend fun runKeywordSearch(query: String) {
        _state.update { it.copy(searching = true) }
        recipeRepository.search(query.trim())
            .onSuccess { recipes ->
                _state.update { it.copy(searching = false, results = recipes, usedSmartSearch = false) }
            }
            .onFailure { error ->
                _state.update { it.copy(searching = false, error = error) }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySearchScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: CommunitySearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Find a recipe") },
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
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.screenPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Try “cheap breakfast with eggs”") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = viewModel::smartSearch, enabled = state.query.isNotBlank()) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "Smart search",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.smartSearch() }),
            )
            Spacer(Modifier.height(Dimens.md))

            when {
                state.results == null && !state.searching -> Column {
                    Text(
                        "Describe what you feel like — ingredients, time, budget — and let smart search do the rest.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dimens.md))
                    listOf(
                        "something spicy with paneer in 15 minutes",
                        "cheap breakfast with eggs",
                        "I only have banana, peanut butter and yogurt",
                        "vegetarian microwave dinner",
                    ).forEach { example ->
                        SuggestionChip(
                            onClick = {
                                viewModel.onQueryChange(example)
                                viewModel.smartSearch()
                            },
                            label = { Text(example) },
                        )
                        Spacer(Modifier.height(Dimens.xs))
                    }
                }

                state.searching -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(110.dp)) }
                }

                state.error != null -> EmptyState(
                    title = "Search hit a snag",
                    message = state.error?.message.orEmpty(),
                    actionLabel = "Try again",
                    onAction = viewModel::smartSearch,
                )

                state.results?.isEmpty() == true -> EmptyState(
                    title = "No matches",
                    message = "Try fewer constraints, or different ingredients.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Dimens.xl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    if (state.usedSmartSearch) {
                        item {
                            Row {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Smart results for your request",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    items(state.results.orEmpty(), key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onOpen = { onOpenRecipe(recipe.id) })
                    }
                }
            }
        }
    }
}
