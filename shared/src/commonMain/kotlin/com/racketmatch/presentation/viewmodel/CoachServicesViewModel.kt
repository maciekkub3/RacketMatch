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
        val priceCents: Int,
    ) : CoachServicesEvent()
    data class UpdateService(
        val serviceId: String,
        val name: String,
        val description: String?,
        val pricingType: String,
        val priceCents: Int,
        val isActive: Boolean,
    ) : CoachServicesEvent()
    data class ToggleActive(val serviceId: String, val isActive: Boolean) : CoachServicesEvent()
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
            is CoachServicesEvent.UpdateService -> updateService(event)
            is CoachServicesEvent.ToggleActive -> toggleActive(event.serviceId, event.isActive)
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

    private fun updateService(event: CoachServicesEvent.UpdateService) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.updateService(
                    serviceId = event.serviceId,
                    name = event.name,
                    description = event.description,
                    pricingType = event.pricingType,
                    priceCents = event.priceCents,
                    isActive = event.isActive,
                )
                load()
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }

    /** Active toggle uses the existing service values, flipping only isActive. */
    private fun toggleActive(serviceId: String, isActive: Boolean) {
        val current = (_state.value as? CoachServicesState.Content)?.services
            ?.firstOrNull { it.id == serviceId } ?: return
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.updateService(
                    serviceId = serviceId,
                    name = current.name,
                    description = current.description,
                    pricingType = current.pricingType.name,
                    priceCents = current.priceCents,
                    isActive = isActive,
                )
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
