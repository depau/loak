package eu.depau.loak.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CF-DL (downloads) at the logic layer: the orphan-unpin decision in [pinnedIdsNeedingUnpin],
 * the pure seam extracted from [DownloadManager.reconcileOrphans]. The decision math runs
 * without DAOs/StateFlows/coroutines — it takes the already-read sets and returns the pins to
 * drop. Covers the project invariants: unpinning preserves songs that remain in other distinct
 * collections, and (see [AudioStore.unpin]) flushing a pin never deletes the file — it only
 * makes it evictable cache.
 */
class DownloadManagerPinningTest {

	private val pinned = setOf("a", "b", "c")

	@Test
	fun manualDownloadStaysPinnedEvenWithoutAnyCollection() {
		// "b" is a manual download and survives; "a" and "c" belong to nothing → orphans
		assertEquals(setOf("a", "c"), pinnedIdsNeedingUnpin(
			pinnedServerIds = pinned,
			manualIds = setOf("b"),
			libraryPinned = false,
			collectionMembership = emptyList(),
			keep = emptySet()
		))
	}

	@Test
	fun libraryPinnedKeepsAllPinnedSongs() {
		assertEquals(emptySet(), pinnedIdsNeedingUnpin(
			pinnedServerIds = pinned,
			manualIds = emptySet(),
			libraryPinned = true,
			collectionMembership = emptyList(),
			keep = emptySet()
		))
	}

	@Test
	fun songInAnotherSubscribedCollectionStaysPinned() {
		// core preserve-other-collections rule: "c" survives via the other collection;
		// only "a" and "b" (no collection, no manual, no library) are orphans
		assertEquals(setOf("a", "b"), pinnedIdsNeedingUnpin(
			pinnedServerIds = pinned,
			manualIds = emptySet(),
			libraryPinned = false,
			collectionMembership = listOf(setOf("c")),
			keep = emptySet()
		))
	}

	@Test
	fun pureOrphanDownloadedByNothingGetsUnpinned() {
		assertEquals(setOf("a", "b"), pinnedIdsNeedingUnpin(
			pinnedServerIds = pinned,
			manualIds = emptySet(),
			libraryPinned = false,
			collectionMembership = listOf(setOf("c")),
			keep = emptySet()
		))
	}

	@Test
	fun keepSetForcesSongsToStayPinned() {
		// "a" and "b" survive via keep; "c" is not kept → orphan
		assertEquals(setOf("c"), pinnedIdsNeedingUnpin(
			pinnedServerIds = pinned,
			manualIds = emptySet(),
			libraryPinned = false,
			collectionMembership = emptyList(),
			keep = setOf("a", "b")
		))
	}

	@Test
	fun songSharedByTwoCollectionsSurvivesOneCollectionStoppingWantingIt() {
		// "a" is in collections A and B; A collapses (its membership is dropped here),
		// B still wants it, so only A-only "x" becomes an orphan
		assertEquals(setOf("x"), pinnedIdsNeedingUnpin(
			pinnedServerIds = setOf("a", "x"),
			manualIds = emptySet(),
			libraryPinned = false,
			collectionMembership = listOf(setOf("a")),
			keep = emptySet()
		))
	}

	@Test
	fun emptyPinnedSetUnpinsNothing() {
		assertEquals(emptySet(), pinnedIdsNeedingUnpin(
			pinnedServerIds = emptySet(),
			manualIds = setOf("a"),
			libraryPinned = false,
			collectionMembership = listOf(setOf("a")),
			keep = emptySet()
		))
	}
}
