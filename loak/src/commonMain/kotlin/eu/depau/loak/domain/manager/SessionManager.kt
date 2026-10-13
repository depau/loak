package eu.depau.loak.domain.manager

import eu.depau.loak.di.addSentryBreadcrumb
import eu.depau.loak.di.recordPii
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import eu.depau.loak.util.systemDeviceName

import io.ktor.client.engine.HttpClientEngine
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.api.model.Role
import dev.zt64.subsonic.api.model.User
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager,
	private val networkStatsManager: NetworkStatsManager,
	/**
	 * The HTTP engine the Subsonic client talks through. Null (default) uses Ktor's own
	 * platform default; tests inject a [io.ktor.client.engine.mock.MockEngine] to serve a
	 * fake Subsonic server without any network. Intentionally optional so production code
	 * never passes it.
	 */
	private val httpEngine: HttpClientEngine? = null
) {
	val isLoggedIn: StateFlow<Boolean>
		field = MutableStateFlow(false)

	/**
	 * Tells servers, and users on one, apart: song ids are only unique per server. Changes on
	 * login and logout; "" when logged out.
	 */
	val serverKey: StateFlow<String>
		field = MutableStateFlow("")

	private var currentUser: User? = null

	/** The logged-in username, as entered at login. */
	val username: String get() = settings.getString("username", "")
	val instanceUrl: String get() = settings.getString("instanceUrl", "")
	/** For servers' own APIs that log in with it (Navidrome's). */
	val password: String get() = settings.getString("password", "")

	/** This device's name, as shown to the user's other devices. */
	val deviceName: String get() = preferenceManager.deviceName.ifBlank { systemDeviceName() }

	/** The Subsonic client name; the server reports it as who last saved the queue. */
	val clientName: String get() = "$CLIENT_NAME ($deviceName)"
	private val mutex = Mutex()
	private val scope = CoroutineScope(IoDispatcher)

	var api: SubsonicClient = createClient(
		instanceUrl = settings.getString("instanceUrl", ""),
		username = settings.getString("username", ""),
		password = settings.getString("password", ""),
	)
		private set

	init {
		isLoggedIn.value = settings.getStringOrNull("username") != null
		updateServerKey()
		if (isLoggedIn.value) getCachedUser()
	}

	private fun createClient(
		instanceUrl: String,
		username: String,
		password: String,
	) = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(
			username = username,
			password = password,
		),
		client = clientName,
		// null HTTP engine resolves to Ktor's platform default; tests inject a MockEngine here
		engine = httpEngine,
		clientConfig = {
			install(UserAgent) {
				agent = CLIENT_NAME
			}
			install(networkStatsManager.ktorPlugin)

			val customHeaders = preferenceManager.customHeadersMap()
			if (customHeaders.isNotEmpty()) {
				defaultRequest {
					customHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		}
	)

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String
	) {
		addSentryBreadcrumb("login", "started")

		val client = createClient(instanceUrl, username, password)

		try {
			client.ping()
			fetchCurrentUser(username, client)
		} catch (e: Exception) {
			// TODO: custom exception instead of the generic "Exception"
			// The message here is user-facing (shown in the login error box); a layer-1
			// Sentry censor (LOGIN_CREDENTIAL_MESSAGE_REGEX) redacts it before it ever
			// reaches Sentry, so keep the human-readable text in the app.
			addSentryBreadcrumb("login", "failed", data = mapOf<String, Any>("error" to (e::class.simpleName ?: "unknown")))
			throw Exception(
				"Failed to connect to the instance. Please check your credentials and try again.",
				e
			)
		}

		settings["instanceUrl"] = instanceUrl
		settings["username"] = username
		settings["password"] = password

		// teach the layer-2 censor this account's real server identifiers ahead
		// of any error the session may produce
		recordPii(instanceUrl)

		api = client
		isLoggedIn.value = true
		updateServerKey()
		addSentryBreadcrumb("login", "success")
	}

	fun logout() {
		addSentryBreadcrumb("login", "logged_out")
		settings["username"] = null
		settings["password"] = null
		isLoggedIn.value = false
		updateServerKey()
		currentUser = null
	}

	private fun updateServerKey() {
		serverKey.value = if (!isLoggedIn.value) ""
		else "$username@$instanceUrl".hashCode().toUInt().toString(16)
	}

	fun refreshClient() {
		addSentryBreadcrumb("login", "client_refreshed")
		api = createClient(
			instanceUrl = settings.getString("instanceUrl", ""),
			username = settings.getString("username", ""),
			password = settings.getString("password", ""),
		)
	}

	/** Prefer a [eu.depau.loak.di.CoverArtId] model in Coil: it picks the size and cache key. */
	fun getCoverArtUrl(coverArtId: String, size: Int) = api.getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${size.coerceAtMost(preferenceManager.coverArtQuality.value)}"
	)


	private suspend fun fetchCurrentUser(
		username: String = settings.getString("username", ""),
		client: SubsonicClient = api
	): User? {
		mutex.withLock {
			if (username.isNotBlank()) {
				recordPii(instanceUrl)
				currentUser = client.getUser(username)
				return currentUser
			}
		}

		// TODO: custom exception instead of the generic "Exception"
		throw Exception("Failed to get current user because the username is blank")
	}

	fun getCachedUser(): User? {
		if (currentUser != null) {
			return currentUser
		}
		scope.launch {
			// The server may be briefly unreachable (slow LAN, sleeping host): a
			// connect timeout here used to escape this coroutine and crash the app
			// via the uncaught-exception handler. Swallow and log instead — sharing
			// just stays disabled until the user fetch succeeds.
			runCatching { fetchCurrentUser() }
				.onFailure { Logger.e("SessionManager", "couldn't fetch current user", it) }
		}
		return currentUser
	}
}

private const val CLIENT_NAME = "Lo'ak"

fun User.hasRole(role: Role): Boolean {
	return this.roles.contains(role)
}

fun User.canShare(): Boolean {
	return this.hasRole(Role.SHARE)
}

fun SessionManager.canUserShare(): Boolean {
	return this.getCachedUser()?.canShare() ?: false
}
