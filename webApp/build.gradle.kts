plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

kotlin {
	wasmJs {
		binaries.executable()
		browser {
			testTask {
				enabled = false
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
	}
}

