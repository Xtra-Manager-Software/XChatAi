package id.xms.xcai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import id.xms.xcai.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _authUiState.value = _authUiState.value.copy(user = user)
            }
        }
    }

    fun signInWithGoogle(webClientId: String) {
        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, error = null)

            val result = repository.signInWithGoogle(webClientId)

            result.onSuccess { user ->
                _authUiState.value = AuthUiState(user = user, isLoading = false)
            }.onFailure { exception ->
                _authUiState.value = _authUiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Sign-in failed"
                )
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _authUiState.value = AuthUiState()
    }

    fun clearError() {
        _authUiState.value = _authUiState.value.copy(error = null)
    }
}
