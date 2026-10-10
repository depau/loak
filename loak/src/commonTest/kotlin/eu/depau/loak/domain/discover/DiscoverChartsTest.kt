package eu.depau.loak.domain.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val CHARTS = listOf(
	DeezerCountryChart("Italy", "IT", 1),
	DeezerCountryChart("Ireland", "IE", 2),
	DeezerCountryChart("Worldwide", "XW", 3),
	DeezerCountryChart("Canada", "CA", 4)
)

class DiscoverChartsTest {
	@Test
	fun chartForFallsBackToWorldwide() {
		assertEquals(1L, chartFor(CHARTS, "IT")?.playlistId)
		// a country Deezer has no chart for, and the "not decided yet" empty ISO
		assertEquals(3L, chartFor(CHARTS, "NZ")?.playlistId)
		assertEquals(3L, chartFor(CHARTS, "")?.playlistId)
		assertNull(chartFor(emptyList(), "IT"))
	}

	@Test
	fun countriesAreGroupedWithoutWorldwide() {
		val grouped = chartCountriesByLetter(CHARTS, "")
		assertEquals(listOf('C', 'I'), grouped.keys.sorted())
		assertEquals(listOf("Ireland", "Italy"), grouped['I']?.map { it.countryName })
	}

	@Test
	fun filterMatchesAnywhereIgnoringCase() {
		assertEquals(listOf("Italy"), chartCountriesByLetter(CHARTS, "taly").values.flatten().map { it.countryName })
		assertEquals(listOf("Canada"), chartCountriesByLetter(CHARTS, " can ").values.flatten().map { it.countryName })
		// Worldwide is pinned above the A–Z list, never in it
		assertEquals(emptyList(), chartCountriesByLetter(CHARTS, "world").values.flatten())
	}
}
