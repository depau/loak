package eu.depau.loak.domain.manager

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException

enum class LoginFailureReason {
	INVALID_ADDRESS,
	SERVER_NOT_FOUND,
	CONNECTION_TIMED_OUT,
	SECURE_CONNECTION_FAILED,
	CONNECTION_FAILED,
	WRONG_CREDENTIALS,
	UNSUPPORTED_AUTHENTICATION,
	INCOMPATIBLE_SERVER,
	UNAUTHORIZED,
	TRIAL_EXPIRED,
	INVALID_RESPONSE,
	HTTP_ERROR,
	REJECTED_BY_SERVER,
	UNKNOWN,
}

class LoginFailureException(
	val reason: LoginFailureReason,
	cause: Throwable,
) : Exception(reason.name, cause)

internal fun Throwable.asLoginFailureException(): LoginFailureException {
	var invalidAddress = false
	var serverNotFound = false
	var timedOut = false
	var secureConnectionFailed = false
	var connectionFailed = false
	var invalidResponse = false
	var httpError = false
	var current: Throwable? = this

	while (current != null) {
		if (current is SubsonicException) {
			val reason = when (current.code) {
				SubsonicErrorCode.WRONG_USERNAME_OR_PASSWORD -> LoginFailureReason.WRONG_CREDENTIALS
				SubsonicErrorCode.TOKEN_AUTH_NOT_SUPPORTED_LDAP,
				SubsonicErrorCode.AUTH_METHOD_NOT_SUPPORTED -> LoginFailureReason.UNSUPPORTED_AUTHENTICATION
				SubsonicErrorCode.INCOMPATIBLE_CLIENT_VERSION,
				SubsonicErrorCode.INCOMPATIBLE_SERVER_VERSION -> LoginFailureReason.INCOMPATIBLE_SERVER
				SubsonicErrorCode.UNAUTHORIZED -> LoginFailureReason.UNAUTHORIZED
				SubsonicErrorCode.TRIAL_EXPIRED -> LoginFailureReason.TRIAL_EXPIRED
				else -> LoginFailureReason.REJECTED_BY_SERVER
			}
			return LoginFailureException(reason, this)
		}

		val type = current::class.simpleName.orEmpty().lowercase()
		val message = current.message.orEmpty().lowercase()
		invalidAddress = invalidAddress || type.contains("urlparser") ||
			(type.contains("illegalargument") && message.contains("url")) ||
			message.contains("code=-1000")
		serverNotFound = serverNotFound || type.contains("unknownhost") ||
			type.contains("unresolvedaddress") || type.contains("nameresolution") ||
			message.contains("unable to resolve host") || message.contains("unknown host") ||
			message.contains("cannot be resolved") ||
			message.contains("name or service not known") ||
			message.contains("nodename nor servname") ||
			message.contains("no address associated with hostname") ||
			message.contains("code=-1003") || message.contains("code=-1006")
		timedOut = timedOut || type.contains("timeout") || message.contains("timed out") ||
			message.contains("code=-1001")
		secureConnectionFailed = secureConnectionFailed || type.contains("ssl") ||
			type.contains("tls") || type.contains("certificate") ||
			type.contains("certpath") || message.contains("certificate") ||
			message.contains("ssl handshake") || message.contains("trust anchor") ||
			message.contains("code=-1200") || message.contains("code=-1201") ||
			message.contains("code=-1202") || message.contains("code=-1203") ||
			message.contains("code=-1204") || message.contains("code=-1205") ||
			message.contains("code=-1206")
		invalidResponse = invalidResponse || type.contains("serialization") ||
			type.contains("jsonconvert") || type.contains("notransformation") ||
			type.contains("contentconvert")
		httpError = httpError || type.endsWith("responseexception")
		connectionFailed = connectionFailed || type.contains("connectexception") ||
			type.contains("darwinhttprequest") || type == "ioexception" ||
			message.contains("connection refused") ||
			message.contains("network is unreachable") ||
			message.contains("no route to host") || message.contains("code=-1004") ||
			message.contains("code=-1005") || message.contains("code=-1009")

		val next = current.cause
		if (next === current) break
		current = next
	}

	val reason = when {
		invalidAddress -> LoginFailureReason.INVALID_ADDRESS
		serverNotFound -> LoginFailureReason.SERVER_NOT_FOUND
		secureConnectionFailed -> LoginFailureReason.SECURE_CONNECTION_FAILED
		timedOut -> LoginFailureReason.CONNECTION_TIMED_OUT
		httpError -> LoginFailureReason.HTTP_ERROR
		invalidResponse -> LoginFailureReason.INVALID_RESPONSE
		connectionFailed -> LoginFailureReason.CONNECTION_FAILED
		else -> LoginFailureReason.UNKNOWN
	}
	return LoginFailureException(reason, this)
}
