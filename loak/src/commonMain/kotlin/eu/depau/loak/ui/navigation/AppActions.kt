package eu.depau.loak.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.DirectNavigationEventInput
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.layouts.refreshSlots

/**
 * What the keyboard shortcuts and the desktop menu bar do, in one place. The `can…` and
 * player state reads are snapshot state, so a menu built from them follows along.
 */
class AppActions(
	private val backStack: MutableList<NavKey>,
	private val backInput: DirectNavigationEventInput,
	private val forwardHistory: ForwardHistory,
	private val player: MediaPlayerViewModel,
	/** Past the login screen: the screens below exist. */
	val inApp: Boolean,
) {
	val playerState get() = player.uiState

	val canRefresh get() = refreshSlots.any { it.refresh != null }
	fun refresh() {
		refreshSlots.lastOrNull { it.refresh != null }?.refresh?.invoke()
	}

	val canGoBack get() = backStack.size > 1
	/** The same path as Esc and the system back gesture, so sheets and screens can intercept it. */
	fun back() = backInput.backCompleted()

	val canGoForward get() = forwardHistory.canGoForward
	fun forward() = forwardHistory.forward(backStack)

	fun search() {
		if (backStack.lastOrNull() !is Screen.Search) backStack.add(Screen.Search(nested = true))
	}

	fun settings() {
		if (backStack.lastOrNull() !is Screen.Settings) backStack.add(Screen.Settings.Root)
	}

	fun about() {
		settings()
		if (backStack.lastOrNull() != Screen.Settings.About) backStack.add(Screen.Settings.About)
	}

	fun playPause() = if (player.uiState.value.isPaused) player.resume() else player.pause()
	fun play() = player.resume()
	fun pause() = player.pause()
	fun next() = player.next()
	fun previous() = player.previous()
}
