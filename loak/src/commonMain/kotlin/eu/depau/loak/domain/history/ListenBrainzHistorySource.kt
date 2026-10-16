package eu.depau.loak.domain.history

import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.manager.PreferenceManager

/**
 * ListenBrainz, keyed on the username the Discover settings already collect — anyone who has
 * set that up gets cross-client listening history with nothing new to fill in.
 *
 * The listens are public, so no token is needed.
 */
class ListenBrainzHistorySource(
	private val client: ListenBrainzClient,
	private val preferenceManager: PreferenceManager
) : RemoteHistorySource {
	override val id = "listenbrainz"

	override val isConfigured get() = preferenceManager.listenBrainzUsername.isNotBlank()

	override suspend fun recentListens(from: Long, to: Long, limit: Int): List<RemoteListen>? =
		client.getListens(preferenceManager.listenBrainzUsername, from, to, limit)
}
