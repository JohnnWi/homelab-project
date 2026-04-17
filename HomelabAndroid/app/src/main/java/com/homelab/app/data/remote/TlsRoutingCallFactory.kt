package com.homelab.app.data.remote

import com.homelab.app.data.repository.ServiceInstancesRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class TlsRoutingCallFactory @Inject constructor(
    private val secureClient: OkHttpClient,
    @param:Named("insecure") private val insecureClient: OkHttpClient,
    private val serviceInstancesRepository: ServiceInstancesRepository
) : Call.Factory {

    override fun newCall(request: Request): Call {
        val explicitAllowSelfSigned = request.header(ALLOW_SELF_SIGNED_HEADER)?.toBooleanStrictOrNull()
        val allowSelfSigned = explicitAllowSelfSigned
            ?: request.header(INSTANCE_ID_HEADER)?.let { instanceId ->
                runBlocking { serviceInstancesRepository.getInstance(instanceId)?.allowSelfSigned == true }
            }
            ?: false

        val sanitizedRequest = request.newBuilder()
            .removeHeader(ALLOW_SELF_SIGNED_HEADER)
            .build()

        return if (allowSelfSigned) {
            insecureClient.newCall(sanitizedRequest)
        } else {
            secureClient.newCall(sanitizedRequest)
        }
    }

    companion object {
        const val ALLOW_SELF_SIGNED_HEADER = "X-Homelab-Allow-Self-Signed"
        private const val INSTANCE_ID_HEADER = "X-Homelab-Instance-Id"
    }
}
