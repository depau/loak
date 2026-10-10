package eu.depau.loak.domain.discover

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No-op: web has no lightweight second stream; previews are an unsupported no-op there. */
class WasmPreviewPlayer : PreviewPlayer {
	private val _state = MutableStateFlow(PreviewPlayerState())
	override val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

	override fun play(url: String) = Unit
	override fun pause() = Unit
	override fun resume() = Unit
	override fun stop() = Unit
}
