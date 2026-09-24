plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlinMultiplatform) apply false
	alias(libs.plugins.kotlinMultiplatformLibrary) apply false
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.composeCompiler) apply false
	alias(libs.plugins.valkyrie) apply false
}

// Compose 1.13.0-alpha01+dev4780 (core + material3) all build on Skiko 0.152.0;
// the Kotlin/Wasm target links exactly one skiko so it must match the whole
// Compose release line, otherwise the runtime glue disagrees with the linker
// (missing skia imports / IrLinkageError). 0.152.0 ships the SKIKO-1183 fix
// (RenderNode snapshot cache stack overflow -> flushAndSubmit "index out of
// bounds" on Wasm); 0.152.0-alpha02 predates it. skiko-js-wasm-runtime is not
// published for the 0.152.x line (max 0.150.1 on Central), so it is dropped
// from the force — the wasm graph only needs skiko-wasm-js.
subprojects {
	configurations.configureEach {
		resolutionStrategy {
			force(
				"org.jetbrains.skiko:skiko:0.152.0",
				"org.jetbrains.skiko:skiko-wasm-js:0.152.0"
			)
		}
	}
}
