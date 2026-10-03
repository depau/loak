package eu.depau.loak.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ForwardHistoryTest {
	private val home = Screen.Home()
	private val album = Screen.CollectionDetail("a", "album")
	private val artist = Screen.ArtistDetail("b")

	/** A back stack that feeds every change to its history, like App does. */
	private class Nav(start: List<NavKey>) {
		val history = ForwardHistory()
		val stack = start.toMutableList()
		fun set(vararg keys: NavKey) {
			val old = stack.toList()
			stack.clear(); stack += keys
			history.onChange(old, stack.toList())
		}
		fun forward() {
			val old = stack.toList()
			history.forward(stack)
			history.onChange(old, stack.toList())
		}
	}

	@Test
	fun backThenForward() {
		val nav = Nav(listOf(home, album, artist))
		nav.set(home, album)
		nav.set(home)
		nav.forward()
		assertEquals<List<NavKey>>(listOf(home, album), nav.stack)
		nav.forward()
		assertEquals<List<NavKey>>(listOf(home, album, artist), nav.stack)
		assertFalse(nav.history.canGoForward)
	}

	@Test
	fun multiPopKeepsOrder() {
		val nav = Nav(listOf(home, album, artist))
		nav.set(home)
		nav.forward()
		nav.forward()
		assertEquals<List<NavKey>>(listOf(home, album, artist), nav.stack)
	}

	@Test
	fun newScreenForgets() {
		val nav = Nav(listOf(home, album))
		nav.set(home)
		nav.set(home, artist)
		assertFalse(nav.history.canGoForward)
	}

	@Test
	fun sheetsAreIgnored() {
		val nav = Nav(listOf(home, album))
		nav.set(home)
		nav.set(home, Screen.PlaybackSpeed)
		nav.set(home)
		nav.forward()
		assertEquals<List<NavKey>>(listOf(home, album), nav.stack)
	}
}
