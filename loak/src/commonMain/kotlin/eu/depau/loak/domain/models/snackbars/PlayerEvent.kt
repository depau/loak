package eu.depau.loak.domain.models.snackbars

import org.jetbrains.compose.resources.StringResource

data class PlayerEvent(
	val resource: StringResource,
	val args: List<Any> = emptyList(),
	val action: StringResource? = null,
	val onAction: (() -> Unit)? = null
)
