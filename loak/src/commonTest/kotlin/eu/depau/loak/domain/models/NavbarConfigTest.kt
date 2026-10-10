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
		assertEquals(custom.tabs + NavbarTab(Id.EXPLORE, false) + NavbarTab(Id.DISCOVER, false), migrated.tabs)
		assertEquals(NavbarConfig.VERSION, migrated.version)
	}

	@Test
	fun everyTabIsInTheDefault() = assertEquals(Id.entries.toSet(), NavbarConfig.default.tabs.map { it.id }.toSet())

	@Test
	fun v9UntouchedGetsTheNewDefault() {
		// v9's default: HOME + PLAYLISTS + EXPLORE on, rest off.
		val v9Default = NavbarConfig(
			listOf(Id.HOME, Id.PLAYLISTS, Id.EXPLORE).map { NavbarTab(it, true) } +
				listOf(Id.ALBUMS, Id.ARTISTS, Id.SONGS, Id.GENRES, Id.SEARCH, Id.RADIOS).map { NavbarTab(it, false) },
			9
		)
		assertEquals(NavbarConfig.default, NavbarConfig.migrate(v9Default))
	}

	@Test
	fun v9CustomisedKeepsItsTabsAndGetsDiscoverOff() {
		val custom = v8Default.copy(version = 9, tabs = v8Default.tabs.reversed())
		val migrated = NavbarConfig.migrate(custom)
		assertEquals(custom.tabs + NavbarTab(Id.DISCOVER, false), migrated.tabs)
		assertEquals(NavbarConfig.VERSION, migrated.version)
	}

	@Test
	fun v10StaysUnchanged() {
		val current = NavbarConfig.default
		assertEquals(current, NavbarConfig.migrate(current))
	}

	@Test
	fun moveSkipsOverHiddenTabs() {
		// Discover hidden between Playlists and Albums: dragging Albums onto Playlists' spot
		// leaves Discover where it was relative to the rest
		val config = NavbarConfig(listOf(Id.HOME, Id.PLAYLISTS, Id.DISCOVER, Id.ALBUMS).map { NavbarTab(it, true) }, 10)
		assertEquals(listOf(Id.HOME, Id.ALBUMS, Id.PLAYLISTS, Id.DISCOVER), config.move(Id.ALBUMS, Id.PLAYLISTS).tabs.map { it.id })
		assertEquals(listOf(Id.PLAYLISTS, Id.DISCOVER, Id.ALBUMS, Id.HOME), config.move(Id.HOME, Id.ALBUMS).tabs.map { it.id })
	}
}
