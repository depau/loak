package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import eu.depau.loak.data.database.entities.GenreEntity
import eu.depau.loak.data.database.relations.GenreWithAlbums
import eu.depau.loak.util.Logger

@Dao
interface GenreDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertGenre(song: GenreEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertGenres(genres: List<GenreEntity>)

	@Query("DELETE FROM GenreEntity WHERE genreName = :genreName")
	suspend fun deleteGenre(genreName: String)

	@Transaction
	@Query("SELECT * FROM GenreEntity ORDER BY albumCount DESC")
	suspend fun getGenres(): List<GenreEntity>

	@Transaction
	@Query("SELECT * FROM GenreEntity ORDER BY albumCount DESC")
	fun getGenresFlow(): Flow<List<GenreEntity>>

	@Transaction
	@Query("SELECT * FROM GenreEntity ORDER BY albumCount DESC")
	suspend fun getGenresWithAlbums(): List<GenreWithAlbums>

	@Transaction
	@Query("SELECT * FROM GenreEntity ORDER BY albumCount DESC")
	fun getGenresWithAlbumsFlow(): Flow<List<GenreWithAlbums>>

	@Query("DELETE FROM GenreEntity")
	suspend fun clearAllGenres()

	@Query("SELECT genreName FROM GenreEntity")
	suspend fun getAllGenreNames(): List<String>

	@Transaction
	suspend fun updateAllGenres(remoteGenres: List<GenreEntity>) {
		val remoteNames = remoteGenres.map { it.genreName }.toSet()
		getAllGenreNames().forEach { localName ->
			if (localName !in remoteNames) {
				Logger.w("GenreDao", "genre $localName no longer exists remotely")
				deleteGenre(localName)
			}
		}
		insertGenres(remoteGenres)
	}

	@Transaction
	suspend fun deleteObsoleteGenres(remoteNames: Set<String>) {
		getAllGenreNames().forEach { localName ->
			if (localName !in remoteNames) {
				Logger.w("GenreDao", "genre $localName no longer exists remotely")
				deleteGenre(localName)
			}
		}
	}
}
