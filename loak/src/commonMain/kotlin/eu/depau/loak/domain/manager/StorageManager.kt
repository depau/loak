package eu.depau.loak.domain.manager

expect class StorageManager {
	/** Where the AudioStore keeps its files; null where there is no file system. */
	fun audioStoreDir(): String?
}
