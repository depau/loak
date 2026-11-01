package eu.depau.loak.domain.history

/** What checking a history source's details before saving them found. */
sealed interface HistoryCheck {
	/** It answered; [listens] is how many listens it holds, when it says. */
	data class Connected(val listens: Long?) : HistoryCheck

	/** Nothing answered: a wrong address, or no network. */
	data object Unreachable : HistoryCheck

	/** It answered, but turned the key down or doesn't know the user. */
	data object Rejected : HistoryCheck

	/** Something answered, but not the way this service does. */
	data object Unexpected : HistoryCheck
}
