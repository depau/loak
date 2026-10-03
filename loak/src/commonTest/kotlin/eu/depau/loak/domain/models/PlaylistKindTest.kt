package eu.depau.loak.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistKindTest {
	private fun parse(name: String, smart: Boolean = false, audioMuse: Boolean = true) =
		parsePlaylistName(name, smart, audioMuse)

	@Test
	fun audioMuseNames() {
		assertEquals(PlaylistName("R&B Groove", PlaylistKind.AudioMuseAutomatic, "_automatic"), parse("R&B Groove_automatic"))
		assertEquals(PlaylistName("Pop (2)", PlaylistKind.AudioMuseAutomatic, "_automatic"), parse("Pop_automatic (2)"))
		assertEquals(PlaylistName("Late night", PlaylistKind.AudioMuseInstant, "_instant"), parse("Late night_instant"))
		assertEquals(PlaylistName("Sonic Fingerprint", PlaylistKind.AudioMuseScheduled, " by AudioMuse-AI"), parse("Sonic Fingerprint by AudioMuse-AI"))
		assertEquals("Chill_automatic", parse("x_automatic").let { PlaylistName("Chill", it.kind, it.suffix).rename("Chill") })
	}

	@Test
	fun otherNames() {
		assertEquals(PlaylistKind.Regular, parse("Road trip").kind)
		assertEquals(PlaylistKind.Regular, parse("automatic stuff").kind)
		assertEquals(PlaylistName("R&B Groove_automatic", PlaylistKind.Regular), parse("R&B Groove_automatic", audioMuse = false))
		assertEquals(PlaylistKind.Smart, parse("Top rated_automatic", smart = true).kind)
	}
}
