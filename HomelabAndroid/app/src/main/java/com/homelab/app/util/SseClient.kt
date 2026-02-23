package com.homelab.app.util

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun connectToSse(url: String, serviceType: ServiceType): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .addHeader("X-Homelab-Service", when(serviceType) {
                ServiceType.PORTAINER -> "Portainer"
                ServiceType.PIHOLE -> "Pihole"
                ServiceType.BESZEL -> "Beszel"
                ServiceType.GITEA -> "Gitea"
                ServiceType.UNKNOWN -> "Unknown"
            })
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(data)
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t)
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }
}
