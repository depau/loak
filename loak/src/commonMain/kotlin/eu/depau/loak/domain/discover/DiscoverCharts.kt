package eu.depau.loak.domain.discover

/** Deezer's "Top Worldwide" chart, where a country has none of its own. */
const val WORLDWIDE_ISO = "XW"

/**
 * The chart [iso] maps to, falling back to Worldwide where that country has no chart (and
 * for "", the automatic pick before Deezer has answered). Null when [charts] is empty.
 * Pure, so it can be tested without the network.
 */
fun chartFor(charts: List<DeezerCountryChart>, iso: String): DeezerCountryChart? =
	charts.firstOrNull { it.countryCode == iso } ?: charts.firstOrNull { it.countryCode == WORLDWIDE_ISO }

/**
 * The chart the Discover shelves show: the country picked in the picker ([savedIso]), or the
 * one Deezer sees the caller from when that is "" (automatic). Null when Deezer has no charts.
 */
suspend fun DeezerClient.activeChart(savedIso: String): DeezerCountryChart? {
	val charts = getCountryCharts()
	if (charts.isEmpty()) return null
	return chartFor(charts, savedIso.ifEmpty { getDetectedCountryIso() })
}

/**
 * The picker's A–Z country list: every chart but Worldwide (pinned above it), keeping only
 * names containing [filter], grouped by initial. Pure, so it can be tested without the network.
 */
fun chartCountriesByLetter(
	charts: List<DeezerCountryChart>,
	filter: String
): Map<Char, List<DeezerCountryChart>> = charts
	.filter { it.countryCode != WORLDWIDE_ISO && it.countryName.contains(filter.trim(), ignoreCase = true) }
	.sortedBy { it.countryName }
	.groupBy { it.countryName.first().uppercaseChar() }
