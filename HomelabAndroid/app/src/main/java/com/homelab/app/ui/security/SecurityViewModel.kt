package com.homelab.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.LocalPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val preferencesRepository: LocalPreferencesRepository,
    private val servicesRepository: com.homelab.app.data.repository.ServicesRepository
) : ViewModel() {

    val isPinSet: StateFlow<Boolean> = preferencesRepository.appPin
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val biometricEnabled: StateFlow<Boolean> = preferencesRepository.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _pin = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            preferencesRepository.appPin.collect { _pin.value = it }
        }
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            preferencesRepository.savePin(pin)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return _pin.value == pin
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun clearSecurity() {
        viewModelScope.launch {
            preferencesRepository.clearSecurity()
        }
    }

    fun checkTailscale() {
        servicesRepository.checkTailscale()
    }
}
