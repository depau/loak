package eu.depau.loak.ui.screens.share.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.ui.core.UiState
import kotlin.time.Clock
import kotlin.time.Duration

class ShareDialogViewModel(
	private val sessionManager: SessionManager
) : ViewModel() {
	val state: StateFlow<UiState<String?>>
		field = MutableStateFlow<UiState<String?>>(UiState.Success(null))

	fun share(
		id: String,
		expiry: Duration?
	) {
		viewModelScope.launch {
			state.value = UiState.Loading()
			try {
				val expiration = expiry?.let { Clock.System.now() + it }
				val url = sessionManager.api
					.createShare(listOf(id), expiresAt = expiration)
					.url
				state.value = UiState.Success(url)
			} catch (e: Exception) {
				state.value = UiState.Error(e)
			}
		}
	}
}
