package com.abbeysbite.app.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.FriendsRepository
import com.abbeysbite.app.data.repository.ProfileRepository
import com.abbeysbite.app.platform.MediaPicker
import com.abbeysbite.app.data.repository.MediaStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YouUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    val pendingRequestCount: Int = 0,
    val isPremium: Boolean = false,
    val editSheetOpen: Boolean = false,
    val editUsername: String = "",
    val editDisplayName: String = "",
    val editBio: String = "",
    val editError: String? = null,
    val saving: Boolean = false,
    val signingOut: Boolean = false,
)

class YouViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
    private val billingManager: BillingManager,
    private val mediaPicker: MediaPicker,
    private val storageRepository: MediaStorageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(YouUiState())
    val state: StateFlow<YouUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            billingManager.premiumState.collect { premium ->
                _state.update { it.copy(isPremium = premium.isPremium) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            profileRepository.load()
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, loading = false) }
                }
                .onFailure {
                    _state.update { it.copy(loading = false) }
                }
            friendsRepository.pendingRequests().onSuccess { requests ->
                _state.update { s -> s.copy(pendingRequestCount = requests.count { !it.outgoing }) }
            }
        }
    }

    fun openEditSheet() {
        val p = _state.value.profile
        _state.update {
            it.copy(
                editSheetOpen = true,
                editUsername = p?.username.orEmpty(),
                editDisplayName = p?.displayName.orEmpty(),
                editBio = p?.bio.orEmpty(),
                editError = null,
            )
        }
    }

    fun closeEditSheet() = _state.update { it.copy(editSheetOpen = false) }

    fun onEditUsername(v: String) = _state.update { it.copy(editUsername = v.lowercase(), editError = null) }
    fun onEditDisplayName(v: String) = _state.update { it.copy(editDisplayName = v, editError = null) }
    fun onEditBio(v: String) = _state.update { it.copy(editBio = v.take(200), editError = null) }

    fun saveProfile() {
        val s = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profileRepository.updateProfile(
                username = s.editUsername.trim().takeIf { it.isNotEmpty() },
                displayName = s.editDisplayName.trim(),
                bio = s.editBio.trim(),
            )
                .onSuccess { profile ->
                    _state.update {
                        it.copy(profile = profile, saving = false, editSheetOpen = false)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, editError = error.message) }
                }
        }
    }

    fun changeAvatar() {
        viewModelScope.launch {
            val image = mediaPicker.pickPhoto() ?: return@launch
            storageRepository.uploadAvatar(image.bytes).onSuccess { url ->
                profileRepository.updateProfile(avatarUrl = url).onSuccess { profile ->
                    _state.update { it.copy(profile = profile) }
                }
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            authRepository.signOut()
            billingManager.logoutUser()
            onSignedOut()
        }
    }
}
