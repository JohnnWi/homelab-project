package com.homelab.app.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header

interface UptimeKumaApi {

    @GET("metrics")
    suspend fun getMetrics(
        @Header("X-Homelab-Service") service: String = "Uptime Kuma",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("Accept") accept: String = "text/plain"
    ): ResponseBody
}
