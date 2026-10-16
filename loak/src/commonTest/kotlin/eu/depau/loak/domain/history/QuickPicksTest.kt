package eu.depau.loak.domain.history

import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A library song with only the fields Quick picks and its inputs look at filled in. */
private fun song(
	id: String,
	playCount: Int = 0,
	rating: Int? = null,
	starred: Boolean = false,
	genre: String? = null,
	artistId: String = if (id.startsWith("album-")) "artist" else "artist-$id",
	albumId: String? = if (id.startsWith("album-")) id.substringBeforeLast("-") else "album-$id"
) = DomainSong(
	id = id,
	title = id,
	artistName = "Artist of $id",
	artistId = artistId,
	albumTitle = albumId,
	albumId = albumId,
	parentId = null,
	comment = null,
	trackNumber = 1,
	discNumber = 1,
	isrc = emptyList(),
	year = 2024,
	genre = genre,
	genres = listOfNotNull(genre),
	moods = emptyList(),
	duration = 180.seconds,
	bpm = null,
	contributors = emptyList(),
	playCount = playCount,
	userRating = rating,
	averageRating = null,
	bitRate = null,
	bitDepth = null,
	sampleRate = null,
	audioChannelCount = null,
	replayGain = null,
	fileSize = 0,
	fileExtension = null,
	mimeType = null,
	filePath = null,
	starredAt = if (starred) Instant.fromEpochSeconds(0) else null,
	coverArtId = null,
	musicBrainzId = null,
	explicitStatus = DomainExplicitStatus.Unknown,
	artists = emptyList(),
	albumArtists = emptyList()
)

/** The library from the actual report: one loved song, and a deep back catalogue behind it. */
private fun backCatalogue() = buildList {
	add(song("dracula", playCount = 9))
	for (i in 1..300) add(song("old-$i", playCount = 300))
}

class QuickPicksTest {
	@Test
	fun aSongOnRepeatLeadsTheList() = runTest {
		// nine plays, all of them this week: it outranks 300 songs with 300 plays each
		val picks = pickQuickPicks(backCatalogue(), heat = mapOf("dracula" to 9.0), recentAlbumIds = emptySet())

		assertEquals(20, picks.size)
		assertEquals("dracula", picks.first().id)
	}

	@Test
	fun repeatsAreFirstAndInHeatOrder() = runTest {
		val library = List(40) { song("song-$it", playCount = 5) }
		val heat = mapOf("song-3" to 12.0, "song-7" to 8.0, "song-9" to 3.0)

		val picks = pickQuickPicks(library, heat, recentAlbumIds = emptySet())

		assertEquals(listOf("song-3", "song-7", "song-9"), picks.take(3).map { it.id })
	}

	@Test
	fun aSongPlayedTwentyTimesADayIsStillPicked() = runTest {
		// used to be excluded outright, which is how a track you love disappeared for days
		val library = List(40) { song("song-$it", playCount = 5) }

		val picks = pickQuickPicks(library, heat = mapOf("song-11" to 100.0), recentAlbumIds = emptySet())

		assertTrue(picks.any { it.id == "song-11" })
	}

	@Test
	fun heatFadesInsteadOfBeingCutOff() = runTest {
		val library = List(40) { song("song-$it", playCount = 5) }
		val hot = pickQuickPicks(library, heat = mapOf("song-11" to 50.0), recentAlbumIds = emptySet())
		val fading = pickQuickPicks(
			library,
			heat = mapOf("song-11" to 0.2, "song-12" to 9.0),
			recentAlbumIds = emptySet()
		)

		assertEquals("song-11", hot.first().id)
		// the same song once it has cooled: still on the list, behind whatever is playing now
		val faded = fading.indexOfFirst { it.id == "song-11" }
		val current = fading.indexOfFirst { it.id == "song-12" }
		assertTrue(faded > current, "the cooling song should sit behind the hot one")
	}

	@Test
	fun dislikedSongsAreNeverPicked() = runTest {
		val library = List(40) { song("song-$it") } + song("hated", rating = 1)

		val picks = pickQuickPicks(library, heat = mapOf("hated" to 99.0), recentAlbumIds = emptySet())

		assertFalse(picks.any { it.id == "hated" })
	}

	@Test
	fun starredAndWellRatedSongsArePicked() = runTest {
		val library = List(40) { song("song-$it") } + song("starred", starred = true) +
			song("rated", rating = 5)

		val picks = pickQuickPicks(library, heat = emptyMap(), recentAlbumIds = emptySet())

		assertTrue(picks.any { it.id == "starred" })
		assertTrue(picks.any { it.id == "rated" })
	}

	@Test
	fun discoveriesComeFromArtistsAlreadyPlayed() = runTest {
		val library = List(4) { song("song-$it", playCount = 3) } + song("stranger")
		val discoveryOfAPlayedArtist = song("similar", artistId = "artist-song-0")
		val discoveryOfAStranger = song("unrelated", artistId = "artist-stranger")

		val picks = pickQuickPicks(library, heat = mapOf("song-0" to 5.0), recentAlbumIds = emptySet()) {
			listOf(discoveryOfAPlayedArtist, discoveryOfAStranger)
		}

		assertTrue(picks.any { it.id == "similar" })
		assertFalse(picks.any { it.id == "unrelated" })
	}

	@Test
	fun genreKeepsSongsToThatGenre() = runTest {
		val library = List(20) { song("dance-$it", genre = "Dance") } +
			List(20) { song("metal-$it", genre = "Metal") }

		val picks = pickQuickPicks(library, heat = emptyMap(), recentAlbumIds = emptySet(), genre = "Dance")

		assertEquals(20, picks.size)
		assertTrue(picks.all { it.genre == "Dance" })
	}

	@Test
	fun aSmallLibraryStillFillsWhatItCan() = runTest {
		val picks = pickQuickPicks(List(3) { song("song-$it") }, heat = emptyMap(), recentAlbumIds = emptySet())

		assertEquals(3, picks.size)
	}

	@Test
	fun anEmptyLibraryPicksNothing() = runTest {
		assertEquals(emptyList(), pickQuickPicks(emptyList(), emptyMap(), emptySet()))
	}

	@Test
	fun twoSongsOfTheSameAlbumNeverPlayBackToBack() = runTest {
		// ten albums of two songs each, all of them eligible
		val library = List(10) { album -> List(2) { song("album-$album-$it", playCount = 4) } }.flatten()

		val picks = pickQuickPicks(library, heat = emptyMap(), recentAlbumIds = emptySet(), random = Random(1))

		assertEquals(20, picks.size)
		val adjacent = picks.zipWithNext().count { (a, b) -> a.albumId == b.albumId }
		assertEquals(0, adjacent)
	}
}

class DecayedHeatTest {
	private val now = 1_700_000_000L
	private val day = 86_400L

	@Test
	fun heatHalvesEveryHalfLife() {
		val heat = decayedHeat(
			plays = mapOf(
				"fresh" to listOf(now),
				"yesterday" to listOf(now - day),
				"threeDays" to listOf(now - 3 * day)
			),
			now = now,
			window = 30.days,
			halfLife = 3.days
		)

		assertEquals(1.0, heat.getValue("fresh"), 0.01)
		assertEquals(0.79, heat.getValue("yesterday"), 0.01)
		assertEquals(0.5, heat.getValue("threeDays"), 0.01)
	}

	@Test
	fun severalPlaysAddUp() {
		val heat = decayedHeat(
			plays = mapOf("twice" to listOf(now, now)),
			now = now,
			window = 30.days,
			halfLife = 3.days
		)

		assertEquals(2.0, heat.getValue("twice"), 0.01)
	}

	@Test
	fun playsOutsideTheWindowDoNotCount() {
		val heat = decayedHeat(
			plays = mapOf("ancient" to listOf(now - 60 * day), "today" to listOf(now)),
			now = now,
			window = 30.days,
			halfLife = 3.days
		)

		assertFalse(heat.containsKey("ancient"))
		assertEquals(1.0, heat.getValue("today"), 0.01)
	}

	@Test
	fun songsWithNothingInTheWindowAreLeftOut() {
		val heat = decayedHeat(
			plays = mapOf("silent" to emptyList()),
			now = now,
			window = 30.days,
			halfLife = 3.days
		)

		assertTrue(heat.isEmpty())
	}
}
