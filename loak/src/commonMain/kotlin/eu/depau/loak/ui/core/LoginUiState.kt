package eu.depau.loak.ui.core

sealed class LoginUiState {
	object Idle : LoginUiState()
	object Loading : LoginUiState()
	object Success : LoginUiState()
	data class Error(val error: Exception) : LoginUiState()
}
