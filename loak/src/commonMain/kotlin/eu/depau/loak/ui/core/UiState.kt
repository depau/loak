package eu.depau.loak.ui.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import eu.depau.loak.util.Logger

sealed class UiState<out T> {
	abstract val data: T?

	data class Loading<T>(override val data: T? = null) : UiState<T>()
	data class Success<T>(override val data: T) : UiState<T>()
	data class Error<T>(
		val error: Exception,
		override val data: T? = null,
	) : UiState<T>()
}

/**
 * For refreshing from the server in the background: what is on screen (the cache) stays
 * there while the server answers and if it fails, so there is no spinner or error flash.
 * States without data, or with [isEmpty] data (nothing cached yet), pass through unchanged.
 */
fun <T> Flow<UiState<T>>.inBackground(
	isEmpty: (T) -> Boolean = { false }
): Flow<UiState<T>> = map { state ->
	val data = state.data
	when {
		state is UiState.Success || data == null || isEmpty(data) -> state
		else -> {
			if (state is UiState.Error) Logger.w("UiState", "Background refresh failed", state.error)
			UiState.Success(data)
		}
	}
}
