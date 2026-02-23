package com.homelab.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.serialization.Serializable

@Singleton
class GlobalEventBus @Inject constructor() {
    private val _authErrors = MutableSharedFlow<ServiceType>(extraBufferCapacity = 1)
    val authErrors = _authErrors.asSharedFlow()

    fun emitAuthError(serviceType: ServiceType) {
        _authErrors.tryEmit(serviceType)
    }
}
