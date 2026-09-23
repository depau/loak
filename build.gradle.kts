plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlinMultiplatform) apply false
	alias(libs.plugins.kotlinMultiplatformLibrary) apply false
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.composeCompiler) apply false
	alias(libs.plugins.valkyrie) apply false
}

// Compose 1.13.0-alpha01 (core + material3) all build on Skiko 0.152.0-alpha02;
// the Kotlin/Wasm target links exactly one skiko so it must match the whole
// Compose release line, otherwise the runtime glue disagrees with the linker
// (missing skia imports / IrLinkageError).
subprojects {
	configurations.configureEach {
		resolutionStrategy {
			force(
				"org.jetbrains.skiko:skiko:0.152.0-alpha02",
				"org.jetbrains.skiko:skiko-wasm-js:0.152.0-alpha02",
				"org.jetbrains.skiko:skiko-js-wasm-runtime:0.152.0-alpha02"
			)
		}
	}
}
