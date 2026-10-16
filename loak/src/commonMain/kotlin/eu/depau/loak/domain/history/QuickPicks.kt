package eu.depau.loak.domain.history

import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.inGenre
import eu.depau.loak.domain.models.isDisliked
import kotlin.math.log2
import kotlin.math.pow
import kotlin.random.Random

/** How many songs Quick picks shows. */
const val QUICK_PICKS_SIZE = 20

/** What each bucket of the list is worth, in the order they fill. */
private const val REPEATS_SIZE = 4
private const val SURE_SIZE = 6
private const val RECENT_SIZE = 10
private const val DISCOVERIES_SIZE = 4

/**
 * Quick picks: twenty songs you'll like, as a pure function of what the sources said — no
 * database, no clock, no network, so the whole thing is testable with a synthetic library.
 *
 * Filled in order: [REPEATS_SIZE] songs that are on repeat right now, then sure likes,
 * recently played songs, a few discoveries, and finally anything at all to top the list up.
 * Only the repeats keep their order — the rest is shuffled, so the same songs don't come back
 * in the same sequence.
 *
 * [heat] is the decayed play count by song id (see [ListeningHistory]), [recentAlbumIds] the
 * albums played in the last month, and [discoveries] the songs the caller fetched for the
 * seeds it is handed — the picks made so far, which is what makes them "like what you like".
 */
suspend fun pickQuickPicks(
	songs: List<DomainSong>,
	heat: Map<String, Double>,
	recentAlbumIds: Set<String>,
	genre: String? = null,
	size: Int = QUICK_PICKS_SIZE,
	random: Random = Random.Default,
	discoveries: suspend (seeds: List<DomainSong>) -> List<DomainSong> = { emptyList() }
): List<DomainSong> {
	val eligible = songs.filter { !it.isDisliked() && it.inGenre(genre) }
	if (eligible.isEmpty()) return emptyList()

	// What is on repeat right now goes first, in heat order, and is never shuffled away: a
	// song you are playing to death leads the list for as long as it stays hot. As its plays
	// age the decay drops it below whatever else is hot, and from there it is just another
	// song competing with the rest — shown, but lower down, which is what YouTube Music does.
	val repeats = eligible
		.filter { (heat[it.id] ?: 0.0) > 0.0 }
		.sortedByDescending { heat.getValue(it.id) }
		.take(REPEATS_SIZE)

	val picks = LinkedHashMap<String, DomainSong>()
	repeats.forEach { picks[it.id] = it }

	fun add(from: List<DomainSong>, count: Int) = from
		.filter { it.id !in picks }
		.weightedSample(count, random) { weight(it, heat) }
		.forEach { picks[it.id] = it }

	// Starred, rated, or played far more than the rest of the library. The all-time count
	// goes through a logarithm: at full weight a back catalogue of 300-play songs buries
	// everything actually being played this month, which is exactly how a track you love
	// gets pushed out of the list.
	val played = eligible.map { it.playCount }.filter { it > 0 }.sorted()
	val highPlays = maxOf(3, played.getOrElse(played.size * 4 / 5) { 3 })
	add(
		eligible.filter { it.starredAt != null || (it.userRating ?: 0) >= 4 || it.playCount >= highPlays },
		SURE_SIZE
	)

	// In rotation lately, off an album played this month or played as a song.
	add(
		eligible.filter {
			(it.albumId != null && it.albumId in recentAlbumIds && it.playCount > 0) ||
				heat.containsKey(it.id)
		},
		RECENT_SIZE
	)

	// Curve balls: like the first picks, and only by artists already played.
	val playedArtists = eligible.asSequence().filter { it.playCount > 0 }.map { it.artistId }.toHashSet()
	add(discoveries(picks.values.take(2)).filter { it.artistId in playedArtists }, DISCOVERIES_SIZE)

	// Short on history: anything played, then anything at all.
	add(eligible.filter { it.playCount > 0 }, size - picks.size)
	add(eligible, size - picks.size)

	val repeatIds = repeats.mapTo(HashSet()) { it.id }
	val rest = picks.values.filter { it.id !in repeatIds }.shuffled(random)
	return (repeats + rest).spreadAlbums()
}

/**
 * How likely a song is to be picked: a flat floor so nothing is impossible, its all-time plays
 * on a log scale, and three times its recent heat, so what is playing right now dominates.
 */
private fun weight(song: DomainSong, heat: Map<String, Double>): Double =
	1.0 + log2(1.0 + song.playCount) + 3.0 * (heat[song.id] ?: 0.0)

/** [count] songs at random, likelier the heavier (Efraimidis–Spirakis). */
private fun List<DomainSong>.weightedSample(
	count: Int,
	random: Random,
	weight: (DomainSong) -> Double
): List<DomainSong> {
	if (count <= 0) return emptyList()
	return map { it to random.nextDouble().pow(1.0 / weight(it).coerceAtLeast(1e-9)) }
		.sortedByDescending { it.second }.take(count).map { it.first }
}

/** Moves songs so that no two from the same album play back to back, where it can. */
private fun List<DomainSong>.spreadAlbums(): List<DomainSong> {
	val songs = toMutableList()
	for (i in 1 until songs.size) {
		if (songs[i].albumId != songs[i - 1].albumId) continue
		val j = (i + 1 until songs.size).firstOrNull { songs[it].albumId != songs[i - 1].albumId }
			?: continue
		songs.add(i, songs.removeAt(j))
	}
	return songs
}
