package eu.depau.loak.ui.components.sheets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangelogUpdateTest {
	@Test
	fun stableVersionsUseNumericAlphaOrdering() {
		val alpha100 = Release(tag = "v1.0.0-alpha100")
		val releases = listOf(
			Release(tag = "nightly"),
			Release(tag = "v1.0.0-alpha9"),
			alpha100,
			Release(tag = "v1.0.0-alpha99")
		)

		assertEquals(alpha100, selectUpdate("v1.0.0-alpha99", "release", "ignored", releases))
		assertNull(selectUpdate("v1.0.0-alpha100", "release", "ignored", releases))
	}

	@Test
	fun nightlyVersionsUseThePublishedCommit() {
		val nightly = Release(tag = "nightly", targetCommit = "abcdef1234567890")

		assertNull(selectUpdate("ignored", "nightly", "abcdef12", listOf(nightly)))
		assertEquals(nightly, selectUpdate("ignored", "nightly", "12345678", listOf(nightly)))
	}
}
