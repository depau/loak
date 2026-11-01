package eu.depau.loak.domain.manager

import eu.depau.loak.di.PlatformType

/**
 * The optional extras the setup wizard offers, in the order it shows them. [id] is stored, so
 * it never changes once shipped.
 */
enum class Feature(val id: String) {
	SmartPlaylists("smart_playlists"),
	AudioMuse("audiomuse"),
	Discover("discover"),
	ListeningHistory("listening_history"),
	NewAlbumAlerts("new_album_alerts")
}

/** Which [Feature]s this device and server can have, and which are set up. */
class FeatureCatalog(
	private val preferenceManager: PreferenceManager,
	private val navidromeManager: NavidromeManager,
	private val audioMuseManager: AudioMuseManager,
	private val lidarrManager: LidarrManager,
	private val platformType: PlatformType
) {
	/** Whether this platform has [feature] at all; the server can still rule it out. */
	fun onPlatform(feature: Feature) = when (feature) {
		// read straight from the browser, where Lidarr, Koito and Last.fm send no CORS headers
		Feature.Discover, Feature.ListeningHistory -> platformType != PlatformType.Web
		Feature.NewAlbumAlerts -> platformType == PlatformType.Android
		Feature.SmartPlaylists, Feature.AudioMuse -> true
	}

	/** Whether [feature] is on. Reads snapshot state, so composables follow changes. */
	fun isSetUp(feature: Feature) = when (feature) {
		Feature.SmartPlaylists -> preferenceManager.smartPlaylistsEnabled
		Feature.AudioMuse -> audioMuseManager.isConfigured
		Feature.Discover -> lidarrManager.isConfigured
		Feature.ListeningHistory -> preferenceManager.listeningHistorySource.isNotEmpty()
		Feature.NewAlbumAlerts -> preferenceManager.newAlbumsNotifications
	}

	/** What this device and server can have, asking the server what it is. */
	suspend fun available(): List<Feature> {
		val canSmart = navidromeManager.serverInfo().canEditSmartPlaylists
		return Feature.entries.filter { onPlatform(it) && (it != Feature.SmartPlaylists || canSmart) }
	}

	/** What the user has been shown, on the wizard's last page or the "New in Lo'ak" page. */
	private val seen get() = seenFeatureIds(preferenceManager.featuresSeen, preferenceManager.integrationsSeen)

	/**
	 * What is worth announcing: available, never shown, and not set up anyway (someone who found
	 * it in Settings needs no telling).
	 */
	suspend fun unannounced(): List<Feature> = available().filter { it.id !in seen && !isSetUp(it) }

	fun markSeen(features: Collection<Feature>) {
		preferenceManager.featuresSeen = (seen + features.map { it.id }).sorted().joinToString(",")
	}

}

/**
 * The ids in [stored] (comma-separated), or, before anything was stored, what the old
 * integrations page's [legacyVersion] listed: 1 the server's features, 2 also Discover.
 */
internal fun seenFeatureIds(stored: String, legacyVersion: Int): Set<String> =
	stored.split(',').filter { it.isNotBlank() }.toSet().ifEmpty {
		buildSet {
			if (legacyVersion >= 1) addAll(listOf(Feature.SmartPlaylists.id, Feature.AudioMuse.id))
			if (legacyVersion >= 2) add(Feature.Discover.id)
		}
	}
