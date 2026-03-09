package com.homelab.app.util

import android.util.Log

/**
 * Centralized logging utility for the Homelab Android application.
 */
object Logger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
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
        Log.d(tag, "State Transition -> $stateName: $stateString")
    }
}
