package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.PortainerApi
import com.homelab.app.data.remote.dto.portainer.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortainerRepository @Inject constructor(
    private val api: PortainerApi
) {

    suspend fun authenticate(url: String, username: String, password: String, allowSelfSigned: Boolean = false): String {
        val fullUrl = url.trimEnd('/') + "/api/auth"
        val credentials = mapOf("username" to username, "password" to password)
        val response = api.authenticate(
            url = fullUrl,
            allowSelfSigned = allowSelfSigned.toString(),
            credentials = credentials
        )
        return response.jwt
    }

    suspend fun authenticateWithApiKey(url: String, apiKey: String, allowSelfSigned: Boolean = false) {
        val cleanUrl = url.trimEnd('/') + "/api/endpoints"
        try {
            api.testApiKey(
                url = cleanUrl,
                allowSelfSigned = allowSelfSigned.toString(),
                apiKey = apiKey
            )
        } catch (e: Exception) {
            // Throw custom error mapped to iOS functionality if it's an HTTP exception
            if (e is retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    throw Exception("Invalid API Key. Check the key and try again.")
                }
            }
            throw e
        }
    }

    suspend fun getEndpoints(instanceId: String): List<PortainerEndpoint> {
        return api.getEndpoints(instanceId = instanceId)
    }

    suspend fun getContainers(instanceId: String, endpointId: Int, all: Boolean = true): List<PortainerContainer> {
        return api.getContainers(instanceId = instanceId, endpointId = endpointId, all = all)
    }

    suspend fun getContainerDetail(instanceId: String, endpointId: Int, containerId: String): ContainerDetail {
        return api.getContainerDetail(instanceId = instanceId, endpointId = endpointId, containerId = containerId)
    }

    suspend fun getContainerStats(instanceId: String, endpointId: Int, containerId: String): ContainerStats {
        return api.getContainerStats(instanceId = instanceId, endpointId = endpointId, containerId = containerId, stream = false)
    }

    suspend fun getContainerLogs(instanceId: String, endpointId: Int, containerId: String, tail: Int = 100): String {
        return api.getContainerLogs(instanceId = instanceId, endpointId = endpointId, containerId = containerId, tail = tail).string()
    }

    suspend fun startContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.start.name)

    suspend fun stopContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.stop.name)

    suspend fun restartContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.restart.name)

    suspend fun killContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.kill.name)

    suspend fun pauseContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.pause.name)

    suspend fun unpauseContainer(instanceId: String, endpointId: Int, containerId: String) =
        api.containerAction(instanceId = instanceId, endpointId = endpointId, containerId = containerId, action = ContainerAction.unpause.name)

    suspend fun removeContainer(instanceId: String, endpointId: Int, containerId: String, force: Boolean = false) {
        api.removeContainer(instanceId = instanceId, endpointId = endpointId, containerId = containerId, force = force)
    }

    suspend fun renameContainer(instanceId: String, endpointId: Int, containerId: String, newName: String) {
        api.renameContainer(instanceId = instanceId, endpointId = endpointId, containerId = containerId, name = newName)
    }

    suspend fun getStacks(instanceId: String, endpointId: Int): List<PortainerStack> {
        val filters = "{\"EndpointID\":$endpointId}"
        return api.getStacks(instanceId = instanceId, filters = filters)
    }

    suspend fun getStackFile(instanceId: String, stackId: Int): String {
        return api.getStackFile(instanceId = instanceId, stackId = stackId).stackFileContent
    }

    suspend fun updateStackFile(instanceId: String, stackId: Int, endpointId: Int, stackFileContent: String) {
        val req = UpdateStackRequest(stackFileContent = stackFileContent)
        api.updateStackFile(instanceId = instanceId, stackId = stackId, endpointId = endpointId, request = req)
    }
}
