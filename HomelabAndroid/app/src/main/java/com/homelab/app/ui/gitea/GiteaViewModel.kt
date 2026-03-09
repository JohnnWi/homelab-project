package com.homelab.app.ui.gitea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.util.Log
import com.homelab.app.R
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.homelab.app.util.Logger
import javax.inject.Inject

enum class RepoSortOrder { RECENT, ALPHA }

@HiltViewModel
class GiteaViewModel @Inject constructor(
    private val repository: GiteaRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _user = MutableStateFlow<GiteaUser?>(null)
    val user: StateFlow<GiteaUser?> = _user

    private val _repos = MutableStateFlow<List<GiteaRepo>>(emptyList())
    val repos: StateFlow<List<GiteaRepo>> = _repos

    private val _orgs = MutableStateFlow<List<GiteaOrg>>(emptyList())
    val orgs: StateFlow<List<GiteaOrg>> = _orgs

    private val _heatmap = MutableStateFlow<List<GiteaHeatmapItem>>(emptyList())
    val heatmap: StateFlow<List<GiteaHeatmapItem>> = _heatmap

    private val _totalBranches = MutableStateFlow(0)
    val totalBranches: StateFlow<Int> = _totalBranches

    private val _sortOrder = MutableStateFlow(RepoSortOrder.RECENT)
    val sortOrder: StateFlow<RepoSortOrder> = _sortOrder

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    init {
        _uiState.onEach { Logger.stateTransition("GiteaViewModel", "uiState", it) }.launchIn(viewModelScope)
    }

    fun fetchAll() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                // We use supervisorScope so that if one async fails, it doesn't cancel the others.
                kotlinx.coroutines.supervisorScope {
                    val uDef = async { repository.getCurrentUser() }
                    val rDef = async { repository.getUserRepos(page = 1, limit = 30) }
                    val oDef = async { repository.getOrgs() }

                    val u = runCatching { uDef.await() }.onFailure {
                        Log.e("Gitea", "Error fetching user profile", it)
                    }.getOrNull()

                    val r = runCatching { rDef.await() }.onFailure {
                        Log.e("Gitea", "Error fetching repos", it)
                    }.getOrDefault(emptyList())

                    val o = runCatching { oDef.await() }.onFailure {
                        Log.e("Gitea", "Error fetching orgs", it)
                    }.getOrDefault(emptyList())

                    Log.d("Gitea", "User: ${u?.login}, Repos found: ${r.size}, Orgs found: ${o.size}")

                    _user.value = u
                    _repos.value = r
                    _orgs.value = o

                    if (u != null) {
                        try {
                            _heatmap.value = repository.getUserHeatmap(u.login)
                        } catch (e: Exception) {
                            // ignore heatmap error
                            Log.e("Gitea", "Error fetching heatmap", e)
                        }
                    }

                    if (r.isNotEmpty()) {
                        launch { // Background branch counting
                            var counts = 0
                            val branchDefs = r.map { repo ->
                                async {
                                    try {
                                        repository.getRepoBranches(owner = repo.owner.login, repo = repo.name).size
                                    } catch (e: Exception) {
                                        0
                                    }
                                }
                            }
                            counts = branchDefs.awaitAll().sum()
                            _totalBranches.value = counts
                        }
                    }

                    if (u == null && _user.value == null) {
                        _uiState.value = UiState.Error(context.getString(R.string.error_gitea_user_profile), retryAction = { fetchAll() })
                    } else {
                        _uiState.value = UiState.Success(Unit)
                    }
                }
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == RepoSortOrder.RECENT) RepoSortOrder.ALPHA else RepoSortOrder.RECENT
    }
}
