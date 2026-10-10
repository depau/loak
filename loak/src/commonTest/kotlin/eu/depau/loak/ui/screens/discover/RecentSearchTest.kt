package eu.depau.loak.ui.screens.discover

import eu.depau.loak.ui.screens.discover.viewmodels.withRecentSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchTest {
	@Test
	fun movesRepeatToTopAndCapsAtTen() {
		assertEquals(listOf("b", "a", "c"), withRecentSearch(listOf("a", "b", "c"), "b"))
		val full = (1..10).map { "q$it" }
		assertEquals(listOf("new") + full.take(9), withRecentSearch(full, "new"))
	}
}
