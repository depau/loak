package eu.depau.loak.ui.screens.explore

import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.entities.GenreEntity
import eu.depau.loak.data.database.relations.GenreWithAlbums
import eu.depau.loak.domain.repositories.HomeLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CF-07 gate: Explore's skeleton loaders. The loading→loaded transition is the
 * actual "no layout jump" contract — the screen shows shimmering placeholders while
 * [ExploreViewModel.state].loadingLibrary is true and real content once it flips
 * false. The VM is built with the loader/ioDispatcher seams so the DAO read is a
 * controlled fake; Koin's `viewModelOf` keeps wiring the real deps in prod.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreSkeletonGatesTest {

	@AfterTest
	fun tearDown() {
		Dispatchers.resetMain()
	}

	/** In-memory [GenreDao]: the Explore test path only reads genres, the rest are no-ops. */
	private fun fakeGenreDao(vararg genres: GenreWithAlbums) = object : GenreDao {
		private val rows = genres.toList()
		override suspend fun insertGenre(song: GenreEntity) {}
		override suspend fun insertGenres(genres: List<GenreEntity>) {}
		override suspend fun deleteGenre(genreName: String) {}
		override suspend fun getGenres() = emptyList<GenreEntity>()
		override fun getGenresFlow(): Flow<List<GenreEntity>> = flowOf(emptyList())
		override suspend fun getGenresWithAlbums() = rows
		override fun getGenresWithAlbumsFlow(): Flow<List<GenreWithAlbums>> = flowOf(rows)
		override suspend fun clearAllGenres() {}
		override suspend fun getAllGenreNames() = emptyList<String>()
	}

	/** Records the loadingLibrary flag + genre count at each point the test samples them. */
	private data class Snapshot(val loadingLibrary: Boolean, val genreCount: Int)

	private fun vm(
		dao: GenreDao,
		loader: suspend () -> HomeLibrary,
		dispatcher: CoroutineDispatcher,
	) = ExploreViewModel(
		genreDao = dao,
		audioMuse = null,
		ioDispatcher = dispatcher,
		libraryLoader = loader
	)

	@Test
	fun placeholdersAreShownDuringLoadThenReplacedByContent() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			// A loader that blocks until the test signals: while it is pending, the
			// skeleton contract (loadingLibrary == true) must still hold.
			val dao = fakeGenreDao(GenreWithAlbums(GenreEntity("Rock", 3, 12), emptyList()))
			val vm = vm(dao = dao, loader = { HomeLibrary(emptyList(), emptyList()) }, dispatcher = StandardTestDispatcher(testScheduler))

			// Before the loader's continuation runs, the state must say "loading":
			// this is what keeps placeholders (not a layout jump) on screen.
			val duringLoad = Snapshot(vm.state.value.loadingLibrary, vm.state.value.genreCount)

			// Drain the fake dispatcher: the loader resolves and the DAO read completes.
			testScheduler.advanceUntilIdle()
			val afterLoad = Snapshot(vm.state.value.loadingLibrary, vm.state.value.genreCount)

			assertEquals(true, duringLoad.loadingLibrary, "skeletons must show while the library loads")
			assertEquals(false, afterLoad.loadingLibrary, "after load, skeletons are replaced by content")
			assertEquals(1, afterLoad.genreCount, "the fake dao's genre surfaces after load")
		} finally {
			Dispatchers.resetMain()
		}
	}
}
