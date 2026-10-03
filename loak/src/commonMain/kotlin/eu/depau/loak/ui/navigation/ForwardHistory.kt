package eu.depau.loak.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey

/**
 * Browser-like forward navigation, which the back stack alone can't do: the screens that
 * back closed, so forward (mouse forward button, Alt+Right) can reopen them. Going anywhere
 * else forgets them. Sheets are left out: they open and close all the time.
 */
class ForwardHistory {
	private val closed = mutableStateListOf<NavKey>()

	val canGoForward get() = closed.isNotEmpty()

	/** Follows a back stack change from [old] to [new]. */
	fun onChange(old: List<NavKey>, new: List<NavKey>) {
		when {
			new.size < old.size && old.subList(0, new.size) == new ->
				closed += old.subList(new.size, old.size).filterNot(::isOverlay).asReversed()
			new.size == old.size + 1 && new.subList(0, old.size) == old ->
				when {
					isOverlay(new.last()) -> Unit
					new.last() == closed.lastOrNull() -> closed.removeAt(closed.lastIndex)
					else -> closed.clear()
				}
			new != old -> closed.clear()
		}
	}

	fun forward(backStack: MutableList<NavKey>) {
		closed.lastOrNull()?.let { backStack.add(it) }
	}

	private fun isOverlay(key: NavKey) = key is Screen.NowPlaying ||
		key is Screen.PlaybackSpeed || key is Screen.SongDetailSheet ||
		key is Screen.ImageView
}
