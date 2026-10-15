package eu.depau.loak.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatCodecNameTest {
	@Test
	fun acronymsAndNames() {
		// names keep their proper spelling, acronyms stay upper-case, whichever input carried it
		assertEquals("Opus", formatCodecName("audio/opus", null))
		assertEquals("Opus", formatCodecName(null, "opus"))
		assertEquals("Vorbis", formatCodecName("audio/ogg", null))
		assertEquals("Vorbis", formatCodecName("audio/vorbis", null))
		assertEquals("Vorbis", formatCodecName(null, "vorbis"))
		assertEquals("FLAC", formatCodecName("audio/flac", "opus"))
		assertEquals("MP3", formatCodecName("audio/mpeg", null))
		assertEquals("AAC", formatCodecName(null, "m4a"))
		assertEquals("WAV", formatCodecName(null, "wav"))
		assertEquals("ALAC", formatCodecName(null, "alac"))
		assertEquals("APE", formatCodecName(null, "ape"))
		assertNull(formatCodecName(null, null))
	}
}
