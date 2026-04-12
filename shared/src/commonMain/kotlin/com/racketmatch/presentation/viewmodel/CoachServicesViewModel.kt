package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CoachServicesState {
    object Loading : CoachServicesState()
    data class Content(val services: List<CoachService>) : CoachServicesState()
    object Error : CoachServicesState()
}

sealed class CoachServicesEvent {
    data class AddService(
        val name: String,
        val description: String?,
        val pricingType: String,
        val priceCents: Int
    ) : CoachServicesEvent()
    data class DeactivateService(val serviceId: String) : CoachServicesEvent()
    object Refresh : CoachServicesEvent()
}

class CoachServicesViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachServicesState>(CoachServicesState.Loading)
    val stateFlow = _state.asStateFlow()

    init { load() }

    fun onEvent(event: CoachServicesEvent) {
        when (event) {
            is CoachServicesEvent.AddService -> addService(event)
            is CoachServicesEvent.DeactivateService -> deactivate(event.serviceId)
            CoachServicesEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachServicesState.Loading
            try {
                val services = coachRepository.getMyServices()
                _state.value = CoachServicesState.Content(services)
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }

    private fun addService(event: CoachServicesEvent.AddService) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.createService(event.name, event.description, event.pricingType, event.priceCents)
                load()
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }

    private fun deactivate(serviceId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.deleteService(serviceId)
                load()
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }
}
