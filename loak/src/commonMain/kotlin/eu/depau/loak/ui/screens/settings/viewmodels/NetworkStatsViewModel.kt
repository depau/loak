package eu.depau.loak.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.hourOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class NetworkStatsViewModel(
	private val dao: NetworkStatsDao,
	private val networkStatsManager: NetworkStatsManager
) : ViewModel() {
	data class State(
		/** Hourly rows of the last 7 days, newest first. */
		val week: List<NetworkStatsEntity>,
		val lastDayStart: Long,
		val syncRuns: List<SyncRunEntity>
	)

	val state: StateFlow<State?>
		field = MutableStateFlow(null)

	init {
		viewModelScope.launch {
			networkStatsManager.flush()
			val now = Clock.System.now()
			state.value = State(
				week = dao.getSince(hourOf(now - 7.days + 1.hours)),
				lastDayStart = hourOf(now - 23.hours),
				syncRuns = dao.getSyncRuns(20)
			)
		}
	}
}
