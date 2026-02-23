package com.homelab.app.ui.gitea

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.data.repository.GiteaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GiteaRepoTab { FILES, COMMITS, ISSUES, BRANCHES }
enum class GiteaViewMode { PREVIEW, CODE }

@HiltViewModel
class GiteaRepoDetailViewModel @Inject constructor(
    private val repository: GiteaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle["owner"])
    val repoName: String = checkNotNull(savedStateHandle["repo"])

    private val _repo = MutableStateFlow<GiteaRepo?>(null)
    val repo: StateFlow<GiteaRepo?> = _repo

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

    private val _isLoadingRepo = MutableStateFlow(true)
    val isLoadingRepo: StateFlow<Boolean> = _isLoadingRepo

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val effectiveBranch: String
        get() = _selectedBranch.value ?: _repo.value?.default_branch ?: "main"

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            _isLoadingRepo.value = true
            _error.value = null
            try {
                // Fetch independently
                _repo.value = runCatching { repository.getRepo(owner, repoName) }.getOrNull()
                _branches.value = runCatching { repository.getRepoBranches(owner, repoName) }.getOrDefault(emptyList())

                if (_repo.value != null) {
                    fetchFiles()
                } else {
                    _error.value = "Impossibile caricare il repository."
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento repository"
            } finally {
                _isLoadingRepo.value = false
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

    fun clearError() {
        _error.value = null
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
            _error.value = null
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
                _error.value = e.localizedMessage ?: "Errore caricamento file"
                _files.value = emptyList()
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun loadFileContent(path: String) {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _error.value = null
            try {
                _viewingFile.value = repository.getFileContent(owner, repoName, path, effectiveBranch)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore apertura file"
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchCommits() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _error.value = null
            try {
                _commits.value = repository.getRepoCommits(owner, repoName, ref = effectiveBranch)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento commits"
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchIssues() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _error.value = null
            try {
                _issues.value = repository.getRepoIssues(owner, repoName)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento issues"
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    private fun fetchBranches() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            _error.value = null
            try {
                _branches.value = repository.getRepoBranches(owner, repoName)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento branches"
            } finally {
                _isLoadingContent.value = false
            }
        }
    }
}
