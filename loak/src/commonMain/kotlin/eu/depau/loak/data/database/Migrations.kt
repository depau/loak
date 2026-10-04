package eu.depau.loak.data.database

import androidx.room3.RoomDatabase

/**
 * Schema upgrades must ship a migration: add an `AutoMigration(from, to)` to the `@Database`
 * (or `addMigrations` for hand-written ones). Data is only dropped on a downgrade, or when coming
 * from a version older than [firstMigratedVersion], for which no schema history exists.
 * Never add a migration starting below [firstMigratedVersion]: Room rejects the overlap.
 */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.migrationPolicy(firstMigratedVersion: Int) =
	fallbackToDestructiveMigrationOnDowngrade(true)
		.fallbackToDestructiveMigrationFrom(true, *IntArray(firstMigratedVersion - 1) { it + 1 })
