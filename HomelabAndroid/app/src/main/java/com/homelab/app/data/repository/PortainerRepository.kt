package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.remote.api.PortainerApi
import com.homelab.app.data.remote.dto.portainer.*
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortainerRepository @Inject constructor(
    private val api: PortainerApi,
    private val settingsManager: SettingsManager
) {

    suspend fun authenticate(url: String, username: String, password: String): String {
        val fullUrl = url.trimEnd('/') + "/api/auth"
        val credentials = mapOf("username" to username, "password" to password)
        val response = api.authenticate(url = fullUrl, credentials = credentials)
        return response.jwt
    }

    suspend fun authenticateWithApiKey(url: String, apiKey: String) {
        val cleanUrl = url.trimEnd('/') + "/api/endpoints"
        try {
            api.testApiKey(url = cleanUrl, apiKey = apiKey)
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

    suspend fun getEndpoints(): List<PortainerEndpoint> {
        return api.getEndpoints()
    }

    suspend fun getContainers(endpointId: Int, all: Boolean = true): List<PortainerContainer> {
        return api.getContainers(endpointId = endpointId, all = all)
    }

    suspend fun getContainerDetail(endpointId: Int, containerId: String): ContainerDetail {
        return api.getContainerDetail(endpointId = endpointId, containerId = containerId)
    }

    suspend fun getContainerStats(endpointId: Int, containerId: String): ContainerStats {
        return api.getContainerStats(endpointId = endpointId, containerId = containerId, stream = false)
    }

    suspend fun getContainerLogs(endpointId: Int, containerId: String, tail: Int = 100): String {
        return api.getContainerLogs(endpointId = endpointId, containerId = containerId, tail = tail).string()
    }

    suspend fun startContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.start.name)

    suspend fun stopContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.stop.name)

    suspend fun restartContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.restart.name)

    suspend fun killContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.kill.name)

    suspend fun pauseContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.pause.name)

    suspend fun unpauseContainer(endpointId: Int, containerId: String) =
        api.containerAction(endpointId = endpointId, containerId = containerId, action = ContainerAction.unpause.name)

    suspend fun removeContainer(endpointId: Int, containerId: String, force: Boolean = false) {
        api.removeContainer(endpointId = endpointId, containerId = containerId, force = force)
    }

    suspend fun renameContainer(endpointId: Int, containerId: String, newName: String) {
        api.renameContainer(endpointId = endpointId, containerId = containerId, name = newName)
    }

    suspend fun getStacks(endpointId: Int): List<PortainerStack> {
        val filters = "{\"EndpointID\":$endpointId}"
        return api.getStacks(filters = filters)
    }

    suspend fun getStackFile(stackId: Int): String {
        return api.getStackFile(stackId = stackId).stackFileContent
    }

    suspend fun updateStackFile(stackId: Int, endpointId: Int, stackFileContent: String) {
        val req = UpdateStackRequest(stackFileContent = stackFileContent)
        api.updateStackFile(stackId = stackId, endpointId = endpointId, request = req)
    }
}
