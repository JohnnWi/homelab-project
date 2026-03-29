package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.GlobalEventBus
import com.homelab.app.util.ServiceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServicesRepositoryTest {

    @Test
    fun `reachability state stays scoped to requested instance id`() = runTest {
        val dao = ReachabilityFakeDao()
        val state = ReachabilitySettingsState()
        val instanceRepository = ServiceInstancesRepository(dao, settingsManager(state))
        val repository = ServicesRepository(
            serviceInstancesRepository = instanceRepository,
            okHttpClient = OkHttpClient(),
            globalEventBus = GlobalEventBus()
        )
        val first = ServiceInstance(
            id = "instance-1",
            type = ServiceType.GITEA,
            label = "Main",
            url = "http://127.0.0.1:1",
            token = "token-1"
        )
        val second = ServiceInstance(
            id = "instance-2",
            type = ServiceType.GITEA,
            label = "Backup",
            url = "http://127.0.0.1:2",
            token = "token-2"
        )

        instanceRepository.saveInstance(first)
        instanceRepository.saveInstance(second)

        repository.checkReachability(first.id)

        val reachability = repository.reachability.first()
        assertEquals(false, reachability[first.id])
        assertNull(reachability[second.id])
    }

    @Test
    fun `reachability probe includes instance id header`() = runTest {
        val dao = ReachabilityFakeDao()
        val state = ReachabilitySettingsState(migrated = MutableStateFlow(true))
        val instanceRepository = ServiceInstancesRepository(dao, settingsManager(state))
        val captured = mutableListOf<Request>()
        val repository = ServicesRepository(
            serviceInstancesRepository = instanceRepository,
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    captured += request
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
            globalEventBus = GlobalEventBus()
        )
        val instance = ServiceInstance(
            id = "instance-radarr",
            type = ServiceType.RADARR,
            label = "Radarr",
            url = "https://radarr.example.com",
            apiKey = "token"
        )

        instanceRepository.saveInstance(instance)

        repository.checkReachability(instance.id, force = true)

        assertEquals("instance-radarr", captured.first().header("X-Homelab-Instance-Id"))
        assertEquals("/api/v3/system/status", captured.first().url.encodedPath)
    }

    private fun settingsManager(state: ReachabilitySettingsState): SettingsManager {
        return mockk(relaxed = true) {
            every { serviceInstancesMigrated } returns state.migrated
            every { preferredInstanceIds } returns state.preferred
            every { preferredInstanceId(any()) } answers {
                val type = invocation.args[0] as ServiceType
                state.preferred.map { it[type] }
            }
            coEvery { getLegacyConnection(any()) } returns null
            coEvery { removeLegacyConnection(any()) } returns Unit
            coEvery { setPreferredInstanceId(any(), any()) } coAnswers {
                val type = invocation.args[0] as ServiceType
                val id = invocation.args[1] as String?
                state.preferred.value = state.preferred.value.toMutableMap().apply { put(type, id) }
            }
            coEvery { setServiceInstancesMigrated(any()) } coAnswers {
                state.migrated.value = invocation.args[0] as Boolean
            }
        }
    }
}

private data class ReachabilitySettingsState(
    val migrated: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val preferred: MutableStateFlow<Map<ServiceType, String?>> = MutableStateFlow(emptyMap())
)

private class ReachabilityFakeDao : ServiceInstanceDao {
    private val state = MutableStateFlow<List<ServiceInstanceEntity>>(emptyList())

    override fun observeAll(): Flow<List<ServiceInstanceEntity>> = state

    override suspend fun getAll(): List<ServiceInstanceEntity> = state.value

    override suspend fun getById(id: String): ServiceInstanceEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun getByType(type: String): List<ServiceInstanceEntity> = state.value.filter { it.type == type }

    override suspend fun upsert(entity: ServiceInstanceEntity) {
        state.value = state.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun upsertAll(entities: List<ServiceInstanceEntity>) {
        val ids = entities.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + entities
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
