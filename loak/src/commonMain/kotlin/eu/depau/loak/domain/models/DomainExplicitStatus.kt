package eu.depau.loak.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
enum class DomainExplicitStatus {
	Explicit,
	Clean,
	Unknown
}
