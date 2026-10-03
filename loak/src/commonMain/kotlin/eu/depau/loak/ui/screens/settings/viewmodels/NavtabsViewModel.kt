package eu.depau.loak.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.ui.core.UiState

class NavtabsViewModel(
	private val settings: Settings
) : ViewModel() {
	private val json = Json

	// one config for every bar, rail and settings page: each gets its own view model
	val state: StateFlow<UiState<NavbarConfig>> get() = shared

	init {
		if (shared.value is UiState.Loading) try {
			shared.value = UiState.Success(loadConfig())
		} catch (e: Exception) {
			shared.value = UiState.Error(e)
		}
	}

	private fun loadConfig(): NavbarConfig {
		val raw = settings.getStringOrNull(NavbarConfig.KEY)
			?: return NavbarConfig.default
		val config: NavbarConfig = json.decodeFromString(raw)
		return NavbarConfig.migrate(config)
	}

	private fun setConfig(newConfig: NavbarConfig) {
		shared.value = UiState.Success(newConfig)
		settings[NavbarConfig.KEY] = json.encodeToString(newConfig)
	}

	fun move(from: Int, to: Int) {
		val config = (state.value as UiState.Success).data
		setConfig(
			config.copy(
				tabs = config.tabs.toMutableList().apply {
					add(to, removeAt(from))
				}
			))
	}

	fun toggleVisibility(id: NavbarTab.Id) {
		val config = (state.value as UiState.Success).data
		val turningOn = config.tabs.any { it.id == id && !it.visible }
		if (turningOn && config.tabs.count { it.visible } >= NavbarConfig.MAX_VISIBLE) return
		setConfig(
			config.copy(
				tabs = config.tabs.map {
					if (it.id == id) it.copy(visible = !it.visible) else it
				}
			)
		)
	}

	private companion object {
		val shared = MutableStateFlow<UiState<NavbarConfig>>(UiState.Loading())
	}
}
