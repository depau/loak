package eu.depau.loak.domain.history

import eu.depau.loak.domain.manager.PreferenceManager

/**
 * The remote listening-history sources Lo'ak can read from, and which one the user picked.
 *
 * Nothing here is a client of its own: [PreferenceManager] is read on every call, so
 * switching the source in settings takes effect on the next ranking instead of on the next
 * app start.
 */
class HistorySourceRegistry(
	private val preferenceManager: PreferenceManager,
	sources: List<RemoteHistorySource>
) {
	private val sources = sources.associateBy { it.id }

	/** Every source, in the order the settings screen lists them. */
	fun all(): List<RemoteHistorySource> = sources.values.toList()

	/**
	 * The source the user picked, or null when they picked none. A source that is picked but
	 * no longer filled in (the username was cleared, the URL removed) is the same as none:
	 * asking it anything would only ever return nothing.
	 */
	fun current(): RemoteHistorySource? =
		sources[preferenceManager.listeningHistorySource]?.takeIf { it.isConfigured }

	/** The source with [id], for a settings screen that has to say what one is missing. */
	fun byId(id: String): RemoteHistorySource? = sources[id]
}
