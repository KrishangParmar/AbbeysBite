package com.abbeysbite.app.features.profile

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.EmptyState
import com.abbeysbite.app.core.designsystem.InitialsAvatar
import com.abbeysbite.app.core.designsystem.SkeletonBox
import com.abbeysbite.app.data.model.FriendProgress
import com.abbeysbite.app.data.model.FriendWithProfile
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.repository.FriendsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class FriendsUiState(
    val tab: Int = 0,
    val loading: Boolean = true,
    val friends: List<FriendWithProfile> = emptyList(),
    val requests: List<FriendWithProfile> = emptyList(),
    val progress: List<FriendProgress> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Profile> = emptyList(),
    val searching: Boolean = false,
    val sentRequestIds: Set<String> = emptySet(),
    val message: String? = null,
)

class FriendsViewModel(
    private val friendsRepository: FriendsRepository,
    private val analytics: Analytics,
    private val notificationSender: com.abbeysbite.app.core.session.NotificationSender,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val friends = friendsRepository.friends().getOrNull() ?: emptyList()
            val requests = friendsRepository.pendingRequests().getOrNull() ?: emptyList()
            val progress = friendsRepository.friendProgress().getOrNull() ?: emptyList()
            _state.update {
                it.copy(loading = false, friends = friends, requests = requests, progress = progress)
            }
        }
    }

    fun selectTab(index: Int) = _state.update { it.copy(tab = index, message = null) }

    fun onSearchChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(searching = true) }
            friendsRepository.searchUsers(query)
                .onSuccess { results ->
                    _state.update { it.copy(searchResults = results, searching = false) }
                }
                .onFailure {
                    _state.update { it.copy(searching = false) }
                }
        }
    }

    fun sendRequest(profile: Profile) {
        viewModelScope.launch {
            friendsRepository.sendRequest(profile.id)
                .onSuccess {
                    analytics.track(AnalyticsEvents.FRIEND_REQUEST_SENT)
                    notificationSender.friendRequest(profile.id)
                    _state.update {
                        it.copy(
                            sentRequestIds = it.sentRequestIds + profile.id,
                            message = "Request sent to @${profile.username}",
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message) }
                }
        }
    }

    fun accept(f: FriendWithProfile) = act {
        friendsRepository.accept(f.friendship.id).onSuccess {
            notificationSender.friendAccepted(f.profile.id)
        }
    }
    fun decline(f: FriendWithProfile) = act { friendsRepository.decline(f.friendship.id) }
    fun cancel(f: FriendWithProfile) = act { friendsRepository.cancel(f.friendship.id) }
    fun unfriend(f: FriendWithProfile) = act { friendsRepository.unfriend(f.friendship.id) }

    private fun act(block: suspend () -> Any) {
        viewModelScope.launch {
            block()
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab) {
                listOf("Friends", "Requests", "Find people").forEachIndexed { index, label ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                if (index == 1 && state.requests.isNotEmpty())
                                    "$label (${state.requests.size})" else label
                            )
                        },
                    )
                }
            }

            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(Dimens.md),
                )
            }

            when (state.tab) {
                0 -> FriendsList(state, viewModel)
                1 -> RequestsList(state, viewModel)
                2 -> FindPeople(state, viewModel)
            }
        }
    }
}

@Composable
private fun FriendsList(state: FriendsUiState, viewModel: FriendsViewModel) {
    when {
        state.loading -> Column(
            Modifier.padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(70.dp)) }
        }

        state.friends.isEmpty() -> EmptyState(
            title = "No friends yet",
            message = "Find people by username and cook alongside each other.",
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            items(state.friends, key = { it.friendship.id }) { friend ->
                val progress = state.progress.find { it.profile.id == friend.profile.id }
                FriendCard(friend, progress, onUnfriend = { viewModel.unfriend(friend) })
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendWithProfile,
    progress: FriendProgress?,
    onUnfriend: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(name = friend.profile.displayOrUsername, size = 44.dp)
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(friend.profile.displayOrUsername, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "@${friend.profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onUnfriend) {
                    Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Shared progress — only what this friend chose to share.
            progress?.let { p ->
                val stats = buildList {
                    p.activeDaysThisWeek?.let { add("$it active days this week") }
                    p.mealsLoggedThisWeek?.let { add("$it meals logged") }
                    p.plantVariety?.let { add("$it plant variety") }
                    p.recipesPublished?.takeIf { it > 0 }?.let { add("$it recipes shared") }
                }
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.sm))
                    Text(
                        stats.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestsList(state: FriendsUiState, viewModel: FriendsViewModel) {
    if (state.requests.isEmpty()) {
        EmptyState(
            title = "No pending requests",
            message = "Requests you send and receive will appear here.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        items(state.requests, key = { it.friendship.id }) { request ->
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialsAvatar(name = request.profile.displayOrUsername, size = 44.dp)
                    Spacer(Modifier.width(Dimens.md))
                    Column(Modifier.weight(1f)) {
                        Text(request.profile.displayOrUsername, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (request.outgoing) "Request sent" else "Wants to be friends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (request.outgoing) {
                        TextButton(onClick = { viewModel.cancel(request) }) { Text("Cancel") }
                    } else {
                        IconButton(onClick = { viewModel.accept(request) }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Accept request",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { viewModel.decline(request) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Decline request",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindPeople(state: FriendsUiState, viewModel: FriendsViewModel) {
    Column(Modifier.padding(Dimens.screenPadding)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by username") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(Dimens.md))
        when {
            state.searching -> SkeletonBox(Modifier.fillMaxWidth().height(70.dp))
            state.searchResults.isEmpty() && state.searchQuery.length >= 2 -> Text(
                "No one found with that username.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                items(state.searchResults, key = { it.id }) { profile ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(Dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InitialsAvatar(name = profile.displayOrUsername, size = 40.dp)
                            Spacer(Modifier.width(Dimens.md))
                            Column(Modifier.weight(1f)) {
                                Text(profile.displayOrUsername, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "@${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (profile.id in state.sentRequestIds) {
                                Text(
                                    "Sent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                IconButton(onClick = { viewModel.sendRequest(profile) }) {
                                    Icon(
                                        Icons.Filled.PersonAdd,
                                        contentDescription = "Add ${profile.displayOrUsername}",
                                        tint = MaterialTheme.colorScheme.primary,
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
