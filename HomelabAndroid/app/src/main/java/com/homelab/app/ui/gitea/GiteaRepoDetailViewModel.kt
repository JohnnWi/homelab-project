package com.homelab.app.ui.gitea

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.homelab.app.util.Logger
import javax.inject.Inject

enum class GiteaRepoTab { FILES, COMMITS, ISSUES, BRANCHES }
enum class GiteaViewMode { PREVIEW, CODE }

@HiltViewModel
class GiteaRepoDetailViewModel @Inject constructor(
    private val repository: GiteaRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle["owner"])
    val repoName: String = checkNotNull(savedStateHandle["repo"])

    private val _uiState = MutableStateFlow<UiState<GiteaRepo>>(UiState.Loading)
    val uiState: StateFlow<UiState<GiteaRepo>> = _uiState

    private val _activeTab = MutableStateFlow(GiteaRepoTab.FILES)
    val activeTab: StateFlow<GiteaRepoTab> = _activeTab

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    private val _selectedBranch = MutableStateFlow<String?>(null)
    val selectedBranch: StateFlow<String?> = _selectedBranch

    private val _files = MutableStateFlow<List<GiteaFileContent>>(emptyList())
    val files: StateFlow<List<GiteaFileContent>> = _files

    private val _viewingFile = MutableStateFlow<GiteaFileContent?>(null)
    val viewingFile: StateFlow<GiteaFileContent?> = _viewingFile

    private val _readme = MutableStateFlow<GiteaFileContent?>(null)
    val readme: StateFlow<GiteaFileContent?> = _readme

    private val _viewMode = MutableStateFlow(GiteaViewMode.PREVIEW)
    val viewMode: StateFlow<GiteaViewMode> = _viewMode

    private val _commits = MutableStateFlow<List<GiteaCommit>>(emptyList())
    val commits: StateFlow<List<GiteaCommit>> = _commits

    private val _issues = MutableStateFlow<List<GiteaIssue>>(emptyList())
    val issues: StateFlow<List<GiteaIssue>> = _issues

    private val _branches = MutableStateFlow<List<GiteaBranch>>(emptyList())
    val branches: StateFlow<List<GiteaBranch>> = _branches

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    val effectiveBranch: String
        get() = _selectedBranch.value ?: (uiState.value as? UiState.Success)?.data?.default_branch ?: "main"

    init {
        _uiState.onEach { Logger.stateTransition("GiteaRepoDetailViewModel", "uiState", it) }.launchIn(viewModelScope)
        fetchRepo()
    }

    fun fetchRepo() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val r = repository.getRepo(owner, repoName)
                _branches.value = runCatching { repository.getRepoBranches(owner, repoName) }.getOrDefault(emptyList())
                _uiState.value = UiState.Success(r)
                fetchFiles()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(ErrorHandler.getMessage(context, e))
            }
        }
    }

    fun setActiveTab(tab: GiteaRepoTab) {
        if (_activeTab.value == tab) return
        _activeTab.value = tab
        if (tab == GiteaRepoTab.FILES) {
            _viewingFile.value = null
        }
        fetchTabContent()
    }

    fun setBranch(branchName: String) {
        _selectedBranch.value = branchName
        _viewingFile.value = null
        _currentPath.value = ""
        fetchTabContent()
    }

    fun navigateToPath(path: String, isFile: Boolean) {
        _currentPath.value = path
        if (isFile) {
            loadFileContent(path)
        } else {
            fetchFiles(path)
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current.isEmpty()) return
        
        if (_viewingFile.value != null) {
            _viewingFile.value = null
            val parts = current.split("/")
            if (parts.size <= 1) {
                _currentPath.value = ""
                fetchFiles("")
            } else {
                val upPath = parts.dropLast(1).joinToString("/")
                _currentPath.value = upPath
                fetchFiles(upPath)
            }
        } else {
            val parts = current.split("/")
            if (parts.size <= 1) {
                _currentPath.value = ""
                fetchFiles("")
            } else {
                val upPath = parts.dropLast(1).joinToString("/")
                _currentPath.value = upPath
                fetchFiles(upPath)
            }
        }
    }

    fun setViewMode(mode: GiteaViewMode) {
        _viewMode.value = mode
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun fetchTabContent() {
        when (_activeTab.value) {
            GiteaRepoTab.FILES -> fetchFiles(_currentPath.value)
            GiteaRepoTab.COMMITS -> fetchCommits()
            GiteaRepoTab.ISSUES -> fetchIssues()
            GiteaRepoTab.BRANCHES -> fetchBranches()
        }
    }

    private fun fetchFiles(path: String = "") {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _viewingFile.value = null
            _actionError.value = null
            try {
                val contents = repository.getRepoContents(owner, repoName, path, effectiveBranch)
                // Sort folders first
                _files.value = contents.sortedWith(compareBy<GiteaFileContent> { !it.isDirectory }.thenBy { it.name.lowercase() })
                
                if (path.isEmpty()) {
                    try {
                        _readme.value = repository.getRepoReadme(owner, repoName, effectiveBranch)
                    } catch (e: Exception) {
                        _readme.value = null
                    }
                } else {
                    _readme.value = null
                }
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
                _files.value = emptyList()
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun loadFileContent(path: String) {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _actionError.value = null
            try {
                _viewingFile.value = repository.getFileContent(owner, repoName, path, effectiveBranch)
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchCommits() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _actionError.value = null
            try {
                _commits.value = repository.getRepoCommits(owner, repoName, ref = effectiveBranch)
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchIssues() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _actionError.value = null
            try {
                _issues.value = repository.getRepoIssues(owner, repoName)
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchBranches() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _actionError.value = null
            try {
                _branches.value = repository.getRepoBranches(owner, repoName)
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            } finally {
                _isLoadingContent.value = false
            }
        }
    }
}
