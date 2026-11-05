import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

kotlin {
	wasmJs {
		binaries.executable()
		browser {
			// the user flows (src/wasmJsTest) in headless Chrome, Firefox and WebKit (Playwright),
			// against the Navidrome fixtures; karma.config.d serves the app's static assets to them.
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
			implementation(libs.ktor.client.mock)
			implementation(libs.kotlinx.coroutines.test)
			implementation(npm("playwright", libs.versions.playwright.get()))
		}
	}
}

tasks.named<KotlinJsTest>("wasmJsBrowserTest") {
	usesNavidrome(this)
	// the large-library flows only on demand (WebLargeLibraryFlows): -Ploak.webLargeLibrary
	val largeLibrary = providers.gradleProperty("loak.webLargeLibrary").isPresent
	if (largeLibrary) usesLargeNavidrome(this)
	environment("LOAK_WEB_LARGE_LIBRARY", largeLibrary.toString())
	// for karma.config.d, which hands it to the tests
	environment("LOAK_UITEST_PORT", uiTestPort().toString())
	// -Ploak.webBrowsers=chrome,firefox,webkit: only those (karma.config.d), e.g. one at a time
	environment("LOAK_WEB_BROWSERS", providers.gradleProperty("loak.webBrowsers").getOrElse(""))
}
