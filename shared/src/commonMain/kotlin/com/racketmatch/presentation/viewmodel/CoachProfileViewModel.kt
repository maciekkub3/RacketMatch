package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.CoachWeeklyAvailability
import com.racketmatch.domain.model.Sport
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed class CoachProfileState {
    object Loading : CoachProfileState()
    data class Content(
        val displayName: String,
        val city: String,
        val bio: String,
        val sports: Set<Sport>,
        val avatarUrl: String,
        val lessonCount: Int,
        val courts: List<String>,
        val services: List<CoachService>,
        val weeklyAvailability: List<CoachWeeklyAvailability>,
    ) : CoachProfileState()
    object Error : CoachProfileState()
}

class CoachProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val coachRepository: CoachRepository,
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachProfileState>(CoachProfileState.Loading)
    val stateFlow = _state.asStateFlow()

    init {
        loadProfile()
        viewModelScope.launch {
            tokenStorage.profileVersionFlow.drop(1).collect { loadProfile() }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch(dispatcher) {
            try {
                val user = profileRepository.getMyProfile()
                val coachProfile = runCatching { coachRepository.getMyCoachProfile() }.getOrNull()
                val bookings = runCatching { coachRepository.getMyBookings() }.getOrDefault(emptyList())
                val services = runCatching { coachRepository.getMyServices() }.getOrDefault(emptyList())
                    .filter { it.isActive }
                val weeklyAvailability = runCatching { coachRepository.getMyAvailability() }.getOrDefault(emptyList())
                val completedLessons = bookings.count { it.status == "CONFIRMED" || it.status == "COMPLETED" }
                _state.value = CoachProfileState.Content(
                    displayName = user.displayName,
                    city = user.city,
                    bio = coachProfile?.bio ?: "",
                    sports = coachProfile?.sports?.toSet() ?: emptySet(),
                    avatarUrl = user.avatarUrl ?: "",
                    lessonCount = completedLessons,
                    courts = coachProfile?.trainingLocations ?: emptyList(),
                    services = services,
                    weeklyAvailability = weeklyAvailability,
                )
            } catch (e: Exception) {
                _state.value = CoachProfileState.Error
            }
        }
    }
}
