package eu.depau.loak.ui.screens.settings.viewmodels

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.ui.core.UiState

class NavtabsViewModel(
	private val settings: Settings,
	private val lidarr: LidarrManager
) : ViewModel() {
	private val json = Json

	init {
		if (shared.value is UiState.Loading) try {
			shared.value = UiState.Success(loadConfig())
		} catch (e: Exception) {
			shared.value = UiState.Error(e)
		}
	}

	// one config for every bar, rail and settings page: each gets its own view model.
	// Discover is left out while it's gated off, but stays saved for when it comes back
	val state: StateFlow<UiState<NavbarConfig>> =
		combine(shared, snapshotFlow { lidarr.discoverShown }) { state, discover -> state.shown(discover) }
			.stateIn(viewModelScope, SharingStarted.Eagerly, shared.value.shown(lidarr.discoverShown))

	/** Whether the Discover tab can be turned on at all (Lidarr needs a working address). */
	val discoverAvailable: Boolean get() = lidarr.isConfigured

	private fun UiState<NavbarConfig>.shown(discover: Boolean) =
		if (this is UiState.Success) UiState.Success(data.shown(discover)) else this

	private fun NavbarConfig.shown(discover: Boolean = lidarr.discoverShown) =
		if (discover) this else copy(tabs = tabs.filter { it.id != NavbarTab.Id.DISCOVER })

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

	/** Moves by position among the tabs shown; hidden ones keep their place. */
	fun move(from: Int, to: Int) {
		val config = (shared.value as UiState.Success).data
		val shown = config.shown().tabs
		setConfig(config.move(shown[from].id, shown[to].id))
	}

	fun toggleVisibility(id: NavbarTab.Id) {
		val config = (shared.value as UiState.Success).data
		val turningOn = config.tabs.any { it.id == id && !it.visible }
		// Discover can't be turned on without a working Lidarr connection (see discoverAvailable)
		if (turningOn && id == NavbarTab.Id.DISCOVER && !lidarr.isConfigured) return
		// ponytail: a hidden Discover doesn't count, so the bar can go one over the limit when it returns
		if (turningOn && config.shown().tabs.count { it.visible } >= NavbarConfig.MAX_VISIBLE) return
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
