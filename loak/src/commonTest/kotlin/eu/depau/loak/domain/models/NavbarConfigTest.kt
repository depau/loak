package eu.depau.loak.domain.models

import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.domain.models.settings.NavbarTab.Id
import kotlin.test.Test
import kotlin.test.assertEquals

class NavbarConfigTest {
	private val v8Default = NavbarConfig(
		listOf(Id.HOME, Id.ALBUMS, Id.PLAYLISTS, Id.ARTISTS).map { NavbarTab(it, true) } +
			listOf(Id.SEARCH, Id.GENRES, Id.SONGS, Id.RADIOS).map { NavbarTab(it, false) },
		8
	)

	@Test
	fun untouchedGetsTheNewDefault() = assertEquals(NavbarConfig.default, NavbarConfig.migrate(v8Default))

	@Test
	fun customisedKeepsItsTabsAndGetsExploreOff() {
		val custom = v8Default.copy(tabs = v8Default.tabs.reversed())
		val migrated = NavbarConfig.migrate(custom)
		assertEquals(custom.tabs + NavbarTab(Id.EXPLORE, false), migrated.tabs)
		assertEquals(NavbarConfig.VERSION, migrated.version)
	}

	@Test
	fun everyTabIsInTheDefault() = assertEquals(Id.entries.toSet(), NavbarConfig.default.tabs.map { it.id }.toSet())
}
