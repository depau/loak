package eu.depau.loak.ui.screens.discover

import com.russhwolf.settings.set
import eu.depau.loak.domain.history.InMemorySettings
import eu.depau.loak.domain.manager.PreferenceManager
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchTest {
	@Test
	fun persistsSharedHistoryAndMaintainsRecentEntries() {
		val settings = InMemorySettings().apply { this["discoverRecentSearches"] = "Alpha\nbeta" }
		val prefs = PreferenceManager(settings)
		assertEquals(listOf("Alpha", "beta"), prefs.searchHistory)

		prefs.addSearchHistory("  ALPHA  ")
		prefs.addSearchHistory(" \r\n ")
		prefs.addSearchHistory("new\r\nsearch")
		assertEquals(listOf("new search", "ALPHA", "beta"), prefs.searchHistory)
		assertEquals(prefs.searchHistory, PreferenceManager(settings).searchHistory)

		(1..10).forEach { prefs.addSearchHistory("q$it") }
		assertEquals((10 downTo 1).map { "q$it" }, prefs.searchHistory)
		prefs.removeSearchHistory("Q5")
		assertEquals((10 downTo 1).filterNot { it == 5 }.map { "q$it" }, prefs.searchHistory)
		assertEquals(prefs.searchHistory, PreferenceManager(settings).searchHistory)
		prefs.clearSearchHistory()
		assertEquals(emptyList(), PreferenceManager(settings).searchHistory)
	}
}
