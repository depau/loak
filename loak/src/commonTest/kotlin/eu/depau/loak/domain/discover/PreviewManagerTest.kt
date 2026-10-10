package eu.depau.loak.domain.discover

import eu.depau.loak.domain.discover.PREVIEW_URL_TTL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PreviewManagerTest {

	private val now = Clock.System.now()
	private fun song(id: String = "s1", previewUrl: String? = "https://cdnt-preview.dzcdn.net/1.mp3", deezerId: Long? = 42L) =
		DiscoverSong(id = id, title = "t", artist = "a", previewUrl = previewUrl, deezerId = deezerId)

	// ---- shouldRefreshPreview ----

	@Test
	fun freshUrlNeedsNoRefresh() {
		// a URL that just arrived from a search has no fetchedAt watermark yet
		assertFalse(shouldRefreshPreview(song(previewUrl = "u"), null, now))
		// fetched moments ago is still fresh
		assertFalse(shouldRefreshPreview(song(previewUrl = "u"), now, now.plus(1.minutes)))
	}

	@Test
	fun exactlyTtlIsStillFresh() {
		assertFalse(shouldRefreshPreview(song(previewUrl = "u"), now, now.plus(PREVIEW_URL_TTL)))
	}

	@Test
	fun staleUrlNeedsRefresh() {
		assertTrue(shouldRefreshPreview(song(previewUrl = "u"), now, now.plus(PREVIEW_URL_TTL).plus(1.minutes)))
	}

	@Test
	fun missingUrlAlwaysNeedsRefresh() {
		assertTrue(shouldRefreshPreview(song(previewUrl = null), now, now))
		assertTrue(shouldRefreshPreview(song(previewUrl = null), null, now))
	}

	// ---- nextPreviewable ----

	@Test
	fun picksFirstPreviewableFromIndex() {
		val songs = listOf(
			song("a", previewUrl = null, deezerId = null),
			song("b", previewUrl = "u2"),
			song("c", previewUrl = null, deezerId = 7L)
		)
		assertEquals(1, nextPreviewable(songs, 0))
		assertEquals(2, nextPreviewable(songs, 2))
	}

	@Test
	fun skipsAllWhenNonePreviewable() {
		val songs = listOf(
			song("a", previewUrl = null, deezerId = null),
			song("b", previewUrl = null, deezerId = null)
		)
		assertEquals(-1, nextPreviewable(songs, 0))
		assertEquals(-1, nextPreviewable(emptyList(), 0))
		assertEquals(-1, nextPreviewable(songs, 5))
	}
}
