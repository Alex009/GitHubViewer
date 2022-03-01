package app.thirtyninth.githubviewer.ui.main.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thirtyninth.githubviewer.data.models.GitHubRepositoryModel
import app.thirtyninth.githubviewer.data.network.NetworkExceptionType
import app.thirtyninth.githubviewer.data.network.Result
import app.thirtyninth.githubviewer.data.repository.GitHubViewerRepository
import app.thirtyninth.githubviewer.preferences.UserPreferences
import app.thirtyninth.githubviewer.utils.UIState
import app.thirtyninth.githubviewer.utils.Variables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RepositoryInfoViewModel @Inject constructor(
    private val repository: GitHubViewerRepository,
    private val userPreferences: UserPreferences,
    state: SavedStateHandle
) : ViewModel() {

    private val _repositoryInfo = MutableSharedFlow<GitHubRepositoryModel>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val repositoryInfo: SharedFlow<GitHubRepositoryModel> = _repositoryInfo.asSharedFlow()

    private val _uiState = MutableStateFlow(UIState.NORMAL)
    val uiState: StateFlow<UIState> get() = _uiState

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> get() = _errorMessage.asStateFlow()

    private val _errorFlow = MutableSharedFlow<NetworkExceptionType>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorFlow: SharedFlow<NetworkExceptionType> = _errorFlow.asSharedFlow()

    private val currentUsername: String = state.get<String>("username").toString()
    private val currentRepositoryName: String = state.get<String>("repository_name").toString()

    init {
        loadRepositoryInfo()
    }

    fun loadRepositoryInfo() = viewModelScope.launch {
        getRepositoryInfo(currentUsername, currentRepositoryName)
    }

    private fun getRepositoryInfo(username: String, repositoryName: String) =
        viewModelScope.launch {
            if (Variables.isNetworkConnected) {
                _uiState.tryEmit(UIState.LOADING)

                when (val result = repository.getRepositoryInfo(username, repositoryName)) {
                    is Result.Success -> {
                        val repo = result.data

                        if (repo != null) {
                            _repositoryInfo.tryEmit(repo)
                            _uiState.tryEmit(UIState.NORMAL)
                        } else {
                            _uiState.tryEmit(UIState.ERROR)
                            _errorFlow.tryEmit(NetworkExceptionType.EMPTY_DATA)
                            _errorMessage.tryEmit("Error: Data is empty")
                        }
                    }

                    is Result.Error -> {
                        _uiState.tryEmit(UIState.NORMAL)
                        _errorFlow.tryEmit(result.type)
                    }
                }
            } else {
                _uiState.tryEmit(UIState.ERROR)
                _errorFlow.tryEmit(NetworkExceptionType.SERVER_ERROR)
            }
        }

    fun logout() = viewModelScope.launch {
        userPreferences.logout()
    }
}