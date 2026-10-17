package eu.depau.loak.ui.util

import eu.depau.loak.domain.manager.LoginFailureReason
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.error_login_connection
import eu.depau.loak.generated.resources.error_login_http
import eu.depau.loak.generated.resources.error_login_incompatible_server
import eu.depau.loak.generated.resources.error_login_invalid_address
import eu.depau.loak.generated.resources.error_login_invalid_response
import eu.depau.loak.generated.resources.error_login_rejected
import eu.depau.loak.generated.resources.error_login_secure_connection
import eu.depau.loak.generated.resources.error_login_server_not_found
import eu.depau.loak.generated.resources.error_login_timeout
import eu.depau.loak.generated.resources.error_login_trial_expired
import eu.depau.loak.generated.resources.error_login_unauthorized
import eu.depau.loak.generated.resources.error_login_unknown
import eu.depau.loak.generated.resources.error_login_unsupported_authentication
import eu.depau.loak.generated.resources.error_login_wrong_credentials
import org.jetbrains.compose.resources.StringResource

val LoginFailureReason.messageResource: StringResource
	get() = when (this) {
		LoginFailureReason.INVALID_ADDRESS -> Res.string.error_login_invalid_address
		LoginFailureReason.SERVER_NOT_FOUND -> Res.string.error_login_server_not_found
		LoginFailureReason.CONNECTION_TIMED_OUT -> Res.string.error_login_timeout
		LoginFailureReason.SECURE_CONNECTION_FAILED ->
			Res.string.error_login_secure_connection
		LoginFailureReason.CONNECTION_FAILED -> Res.string.error_login_connection
		LoginFailureReason.WRONG_CREDENTIALS -> Res.string.error_login_wrong_credentials
		LoginFailureReason.UNSUPPORTED_AUTHENTICATION ->
			Res.string.error_login_unsupported_authentication
		LoginFailureReason.INCOMPATIBLE_SERVER -> Res.string.error_login_incompatible_server
		LoginFailureReason.UNAUTHORIZED -> Res.string.error_login_unauthorized
		LoginFailureReason.TRIAL_EXPIRED -> Res.string.error_login_trial_expired
		LoginFailureReason.INVALID_RESPONSE -> Res.string.error_login_invalid_response
		LoginFailureReason.HTTP_ERROR -> Res.string.error_login_http
		LoginFailureReason.REJECTED_BY_SERVER -> Res.string.error_login_rejected
		LoginFailureReason.UNKNOWN -> Res.string.error_login_unknown
	}
