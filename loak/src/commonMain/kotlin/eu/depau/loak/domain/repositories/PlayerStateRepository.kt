package eu.depau.loak.domain.repositories

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger

class PlayerStateRepository(
	private val settings: Settings
) {
	private val json = Json {
		ignoreUnknownKeys = true
	}

	private val _state = MutableStateFlow(loadState())
	val state: StateFlow<PlayerUiState?> = _state.asStateFlow()

	suspend fun setState(value: PlayerUiState) {
		try {
			settings.putString(KEY_STATE, json.encodeToString(value))
			_state.value = value
		} catch (ex: SerializationException) {
			Logger.e("PlayerStateRepository", "failed to serialise state", ex)
		} catch (ex: Exception) {
			Logger.e("PlayerStateRepository", "failed to save state", ex)
		}
	}

	private fun loadState(): PlayerUiState? {
		if (!settings.hasKey(KEY_STATE)) return null
		return try {
			json.decodeFromString<PlayerUiState>(settings.getString(KEY_STATE, ""))
		} catch (ex: SerializationException) {
			Logger.e("PlayerStateRepository", "failed to deserialise state", ex)
			null
		} catch (ex: Exception) {
			Logger.e("PlayerStateRepository", "failed to read state", ex)
			null
		}
	}

	private companion object {
		const val KEY_STATE = "player_state"
	}
}
