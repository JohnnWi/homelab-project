package com.homelab.app.util

import android.util.Log

/**
 * Centralized logging utility for the Homelab Android application.
 */
object Logger {
    fun d(tag: String, message: String) {
        LogStore.add(LogLevel.DEBUG, tag, message)
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        LogStore.add(LogLevel.INFO, tag, message)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        LogStore.add(LogLevel.WARN, tag, message)
        Log.w(tag, message)
    }

    fun net(tag: String, message: String) {
        LogStore.add(LogLevel.NET, tag, message)
        Log.i(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val formatted = if (throwable?.message.isNullOrBlank()) message else "$message (${throwable?.message})"
        LogStore.add(LogLevel.WARN, tag, formatted)
        Log.e(tag, message, throwable)
    }

    fun stateTransition(tag: String, stateName: String, state: UiState<*>) {
        val stateString = when (state) {
            is UiState.Idle -> "Idle"
            is UiState.Loading -> "Loading"
            is UiState.Success -> "Success"
            is UiState.Error -> "Error(${state.message})"
            is UiState.Offline -> "Offline"
        }
        val message = "State Transition -> $stateName: $stateString"
        LogStore.add(LogLevel.DEBUG, tag, message)
        Log.d(tag, message)
    }
}
