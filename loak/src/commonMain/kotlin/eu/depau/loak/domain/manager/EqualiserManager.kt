package eu.depau.loak.domain.manager

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import eu.depau.loak.domain.models.settings.EqualiserConfig
import eu.depau.loak.util.Logger

class EqualiserManager(
	private val settings: Settings
) {
	private val json = Json

	private val _config = MutableStateFlow(loadConfig())
	val config: StateFlow<EqualiserConfig> = _config.asStateFlow()

	suspend fun setConfig(value: EqualiserConfig) {
		try {
			settings.putString(KEY_CONFIG, json.encodeToString(value))
			_config.value = value
		} catch (ex: SerializationException) {
			Logger.e("EqualiserManager", "failed to serialise config", ex)
		} catch (ex: Exception) {
			Logger.e("EqualiserManager", "failed to save config", ex)
		}
	}

	private fun loadConfig(): EqualiserConfig {
		if (!settings.hasKey(KEY_CONFIG)) return EqualiserConfig()
		return try {
			json.decodeFromString<EqualiserConfig>(settings.getString(KEY_CONFIG, ""))
		} catch (ex: SerializationException) {
			Logger.e("EqualiserManager", "failed to deserialise config", ex)
			EqualiserConfig()
		} catch (ex: Exception) {
			Logger.e("EqualiserManager", "failed to read config", ex)
			EqualiserConfig()
		}
	}

	private companion object {
		const val KEY_CONFIG = "equaliser_config"
	}
}
