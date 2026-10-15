plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

kotlin {
	wasmJs {
		binaries.executable()
		browser {
			// the user flows (src/wasmJsTest) in headless Chrome and Firefox, against the
			// Navidrome fixture; karma.config.d serves the app's static assets to them.
			// ponytail: Karma, since the new test {} DSL doesn't support wasmJs yet
			testTask {
				useKarma {
					useChromeHeadless()
					useFirefoxHeadless()
				}
			}
		}
	}

	sourceSets {
		commonMain.dependencies {
			implementation(project(":loak"))
			implementation(compose.runtime)
			implementation(compose.foundation)
			implementation(compose.ui)
			implementation(libs.koin.core)
		}
		wasmJsTest.dependencies {
			implementation(project(":loakUiTest"))
			implementation(libs.kotlinx.browser)
			implementation(libs.bundles.androidx.lifecycle)
		}
	}
}

tasks.named("wasmJsBrowserTest") { usesNavidrome(this) }
