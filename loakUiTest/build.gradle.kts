import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.kotlinMultiplatformLibrary)
}

// The user flows every platform's UI test suite runs (UserFlows), written once
// against Compose's multiplatform test API. Each app module's tests subclass
// UserFlows with a launcher that starts that platform's real app; see
// NavidromeFixture (buildSrc) for the server they run against.
kotlin {
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	android {
		namespace = "eu.depau.loak.uitest"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	compilerOptions {
		optIn.add("androidx.compose.ui.test.ExperimentalTestApi")
	}

	sourceSets {
		commonMain.dependencies {
			api(libs.cmp.ui.test)
			api(kotlin("test"))
		}
		// kotlin-test only picks its framework for test source sets: name it here
		named("desktopMain").dependencies {
			api(kotlin("test-junit"))
		}
		androidMain.dependencies {
			api(kotlin("test-junit"))
		}
	}
}
