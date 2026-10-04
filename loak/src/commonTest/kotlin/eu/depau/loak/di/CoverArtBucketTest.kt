package eu.depau.loak.di

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverArtBucketTest {
	@Test
	fun bucketsByDisplaySizeCappedBySetting() {
		assertEquals(300, coverArtBucket(48, 4096))
		assertEquals(300, coverArtBucket(300, 4096))
		assertEquals(600, coverArtBucket(301, 4096))
		assertEquals(4096, coverArtBucket(601, 4096))
		assertEquals(4096, coverArtBucket(null, 4096))
		assertEquals(4096, coverArtBucket(COVER_ART_FULL, 4096))
		// Low (512) caps the medium bucket too
		assertEquals(512, coverArtBucket(500, 512))
		assertEquals(300, coverArtBucket(100, 512))
	}
}
