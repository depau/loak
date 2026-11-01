package eu.depau.loak.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureCatalogTest {
	@Test
	fun usersOfTheOldIntegrationsPageHaveSeenOnlyWhatItListed() {
		assertEquals(emptySet(), seenFeatureIds("", 0))
		assertEquals(setOf("smart_playlists", "audiomuse"), seenFeatureIds("", 1))
		// listening history and alerts were never on it: they are still news
		assertEquals(setOf("smart_playlists", "audiomuse", "discover"), seenFeatureIds("", 2))
	}

	@Test
	fun storedIdsReplaceTheOldPagesVersion() {
		assertEquals(setOf("listening_history"), seenFeatureIds("listening_history", 2))
		assertEquals(setOf("discover", "audiomuse"), seenFeatureIds("audiomuse,discover", 0))
	}
}
