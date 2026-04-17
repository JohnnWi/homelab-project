package com.homelab.app.data.remote

import com.homelab.app.data.repository.ServiceInstancesRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class TlsClientSelector @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val secureClient: OkHttpClient,
    @param:Named("insecure") private val insecureClient: OkHttpClient
) {
    fun forAllowSelfSigned(allowSelfSigned: Boolean): OkHttpClient {
        return if (allowSelfSigned) insecureClient else secureClient
    }

    suspend fun forInstance(instanceId: String): OkHttpClient {
        val allowSelfSigned = serviceInstancesRepository.getInstance(instanceId)?.allowSelfSigned == true
        return forAllowSelfSigned(allowSelfSigned)
    }
}
