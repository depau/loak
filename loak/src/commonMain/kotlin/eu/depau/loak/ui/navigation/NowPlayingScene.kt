@file:Suppress("UNCHECKED_CAST")

package eu.depau.loak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * The open player's back stack entry. The player draws itself over the whole window
 * ([PlayerLayer][eu.depau.loak.ui.screens.nowPlaying.PlayerLayer]), so this scene draws nothing
 * and leaves the screen under it showing.
 */
private class PlayerMarkerScene<T : Any>(
	override val key: Any,
	entry: NavEntry<T>,
	override val previousEntries: List<NavEntry<T>>,
	override val overlaidEntries: List<NavEntry<T>>
) : OverlayScene<T> {
	override val entries = listOf(entry)
	override val content = @Composable {}
	override fun equals(other: Any?) = other is PlayerMarkerScene<*> && key == other.key
	override fun hashCode() = key.hashCode()
}

class NowPlayingSceneStrategy<T : Any> : SceneStrategy<T> {

	override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
		val entry = entries.lastOrNull() ?: return null
		if (entry.metadata[PlayerKey] != true) return null
		// previousEntries is empty on purpose: it makes this scene a back-stack root, so the
		// navigation back handler of the scene underneath sees no entry to pop and disables
		// itself. The player's own NavigationBackHandler in PlayerLayer is then the only
		// enabled default handler while the player is up, and receives every predictive back
		// gesture (which drives the sheet down with the swipe) instead of racing NavDisplay's.
		return PlayerMarkerScene(entry.contentKey as T, entry, emptyList(), entries.dropLast(1))
	}

	companion object {
		object PlayerKey : NavMetadataKey<Boolean>

		fun player() = metadata { put(PlayerKey, true) }
	}
}
