package eu.depau.loak.domain.models.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NavbarConfig(
	val tabs: List<NavbarTab>,
	val version: Int
) {
	companion object {
		const val KEY = "navbarConfig"
		const val VERSION = 9

		/** Tabs that can be on at once; Library always comes after them. */
		const val MAX_VISIBLE = 4

		val default = NavbarConfig(
			tabs = listOf(
				NavbarTab(NavbarTab.Id.HOME, true),
				NavbarTab(NavbarTab.Id.PLAYLISTS, true),
				NavbarTab(NavbarTab.Id.EXPLORE, true),
				NavbarTab(NavbarTab.Id.ALBUMS, false),
				NavbarTab(NavbarTab.Id.ARTISTS, false),
				NavbarTab(NavbarTab.Id.SONGS, false),
				NavbarTab(NavbarTab.Id.GENRES, false),
				NavbarTab(NavbarTab.Id.SEARCH, false),
				NavbarTab(NavbarTab.Id.RADIOS, false)
			),
			version = VERSION
		)

		/** Version 8's default: who still has it never changed their tabs. */
		private val defaultV8 = listOf(
			NavbarTab(NavbarTab.Id.HOME, true),
			NavbarTab(NavbarTab.Id.ALBUMS, true),
			NavbarTab(NavbarTab.Id.PLAYLISTS, true),
			NavbarTab(NavbarTab.Id.ARTISTS, true),
			NavbarTab(NavbarTab.Id.SEARCH, false),
			NavbarTab(NavbarTab.Id.GENRES, false),
			NavbarTab(NavbarTab.Id.SONGS, false),
			NavbarTab(NavbarTab.Id.RADIOS, false)
		)

		/**
		 * A saved config in today's shape: version 8 untouched gets the new default, a
		 * customised one keeps its tabs and gets Explore, off. Older ones start over.
		 */
		fun migrate(config: NavbarConfig): NavbarConfig = when (config.version) {
			VERSION -> config
			8 -> if (config.tabs == defaultV8) default
			else NavbarConfig(config.tabs + NavbarTab(NavbarTab.Id.EXPLORE, false), VERSION)
			else -> default
		}
	}
}
