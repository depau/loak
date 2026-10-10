package eu.depau.loak.domain.discover

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryMatcherTest {
	@Test
	fun normalizeTitleStripsFeatCredits() {
		assertEquals("daft punk get lucky", normalizeTitle("Daft Punk - Get Lucky (feat. Pharrell Williams)"))
		assertEquals("song", normalizeTitle("Song (feat. X)"))
		assertEquals("song", normalizeTitle("Song ft. Y"))
		assertEquals("song", normalizeTitle("Song Feat. Z"))
	}

	@Test
	fun normalizeTitleStripsBracketedEditions() {
		assertEquals("song", normalizeTitle("Song (Remastered 2011)"))
		assertEquals("song", normalizeTitle("Song (2020 Remaster)"))
		assertEquals("song", normalizeTitle("Song (Radio Edit)"))
		assertEquals("song", normalizeTitle("Song [Live]"))
		// a non-edition parenthetical is stripped too
		assertEquals("song", normalizeTitle("Song (Original Mix)"))
	}

	@Test
	fun normalizeTitleFoldsCase() {
		assertEquals("let it be", normalizeTitle("Let It Be"))
		assertEquals("a minor", normalizeTitle("A-Minor"))
	}

	@Test
	fun normalizeArtistStripsDiacritics() {
		assertEquals("bjork", normalizeArtist("Björk"))
		assertEquals("motley crue", normalizeArtist("Mötley Crüe"))
		assertEquals("ac dc", normalizeArtist("AC/DC"))
		assertEquals("strasse", normalizeArtist("Straße"))
	}

	@Test
	fun normalizeTitleStripsDiacriticsAndCombines() {
		assertEquals("aenima", normalizeTitle("Ænima (2020 Remaster)"))
		assertEquals("strasse", normalizeTitle("Straße (feat. K) (Remastered)"))
	}

	@Test
	fun normalizeTitleCollapsesPunctuation() {
		assertEquals("u2 with or without you", normalizeTitle("U2 - With or Without You"))
		assertEquals("ac dc highway to hell", normalizeTitle("AC/DC - Highway to Hell"))
	}
}
