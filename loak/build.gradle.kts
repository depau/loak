import com.google.devtools.ksp.gradle.KspAATask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.kotlinMultiplatformLibrary)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.valkyrie)
	alias(libs.plugins.ksp)
	alias(libs.plugins.androidx.room3)
	alias(libs.plugins.sentryKmp)
}

sentryKmp {
	autoInstall {
		cocoapods.enabled = false
	}
}

configurations.all {
	// remove material 2
	exclude(group = "org.jetbrains.compose.material", module = "material")
	exclude(group = "androidx.compose.material", module = "material")
	// cache SNAPSHOT dependencies for less time, default 24h
	resolutionStrategy.cacheChangingModulesFor(1, "hours")
}

// keep the Compose resources Res class in a stable package independent of
// project/module naming (used by eu.depau.loak.generated.resources imports)
compose.resources {
	packageOfResClass = "eu.depau.loak.generated.resources"
	// loakDesktop's menu bar uses the app's strings
	publicResClass = true
}

valkyrie {
	packageName = "eu.depau.loak.icons"
	generateAtSync = true
	outputDirectory = layout.buildDirectory.dir("generated/sources/valkyrie")

	iconPack {
		name = "Icons"
		targetSourceSet = "commonMain"

		nested {
			name = "Brand"
			sourceFolder = "brand"
		}

		nested {
			name = "Outlined"
			sourceFolder = "outlined"
		}

		nested {
			name = "Filled"
			sourceFolder = "filled"
		}
	}
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
	dependsOn("generateValkyrieImageVector")
	dependsOn(generateSentryBuildInfo)
}

// no idea why ksp tasks depend on valkyrie
tasks.withType<KspAATask>().configureEach {
	dependsOn("generateValkyrieImageVector")
	dependsOn(generateSentryBuildInfo)
}

// The exact commit this build came from, baked in as a commonMain constant so
// Sentry events (`options.dist`) can be traced back to a source commit even for
// nightlies and desktop dev builds. Read every time the task runs; a checkout
// without a HEAD (shallow/pristine archive) falls back to the short GitHub SHA.
val generateSentryBuildInfo = tasks.register("generateSentryBuildInfo") {
	val outDir = layout.buildDirectory.dir("generated/sentryBuildInfo/commonMain/kotlin/eu/depau/loak/di")
	val projDir = rootProject.projectDir
	inputs.property("commit", providers.environmentVariable("GITHUB_SHA").orElse("unknown"))
	outputs.dir(outDir)
	// make the baked-in commit visible to commonMain compilations; like the iOS
	// workaround below, the generated source set has to be wired explicitly.
	kotlin.sourceSets["commonMain"].kotlin.srcDir(layout.buildDirectory.dir("generated/sentryBuildInfo/commonMain/kotlin"))

	doLast {
		val sha = runCatching {
			ProcessBuilder("git", "rev-parse", "--short=8", "HEAD")
				.directory(projDir)
				.start()
				.inputStream.bufferedReader().readText().trim()
		}.getOrNull()?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
			?: System.getenv("GITHUB_SHA")?.takeLast(8)
			?: "unknown"

		outDir.get().asFile.mkdirs()
		outDir.get().file("SentryBuildInfo.kt").asFile.writeText(
			"""
package eu.depau.loak.di

/** Baked-in short git SHA (or "unknown") for Sentry build attribution. */
internal const val SENTRY_BUILD_COMMIT: String = "$sha"
"""
		)
	}
}

tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
	// “truly horrifying workaround” for a crash in SearchScreen.kt
	// https://youtrack.jetbrains.com/issue/KT-84055/Reference-to-lambda-in-lambda-in-function-TextField-can-not-be-evaluated#focus=Comments-27-13188532.0-0
	val tmp = layout.buildDirectory.dir("generated/iosWorkaround/commonMain/kotlin").get()
	kotlin.sourceSets["commonMain"].kotlin.srcDir(tmp)

	doFirst {
		tmp.asFile.mkdirs()
		tmp.file("TextFieldDecorator.kt").asFile.writeText(
			"""
package androidx.compose.foundation.text.input

import androidx.compose.runtime.Composable

public fun interface TextFieldDecorator {
    @Suppress("ComposableLambdaParameterNaming")
    @Composable
    public fun Decoration(innerTextField: @Composable () -> Unit)
}
"""
		)
	}
	doLast {
		tmp.asFile.deleteRecursively()
	}
}

kotlin {
	listOf(
		iosArm64(),
		iosSimulatorArm64()
	).forEach { target ->
		target.binaries.framework {
			baseName = "ComposeApp"
			isStatic = true
		}
	}

	wasmJs {
		browser()
	}

	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	android {
		namespace = "eu.depau.loak"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()

		androidResources.enable = true

		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}

		packaging {
			resources {
				excludes += "/okhttp3/**"
				excludes += "/*.properties"
				excludes += "/org/antlr/**"
				excludes += "/com/android/tools/smali/**"
				excludes += "/org/eclipse/jgit/**"
				excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
				excludes += "/org/bouncycastle/**"
				excludes += "/META-INF/{AL2.0,LGPL2.1}"
			}
		}

		buildToolsVersion = "37.0.0"
	}

	sourceSets {
		commonMain.dependencies {
			implementation(libs.bundles.cmp)
			implementation(libs.bundles.ktor)
			implementation(libs.bundles.coil)
			implementation(libs.bundles.cmpThirdParty)
			implementation(libs.bundles.androidx.lifecycle)
			implementation(libs.bundles.room)
			implementation(libs.bundles.koin)

			implementation(libs.androidx.navigation3.ui)
			implementation(libs.kotlinx.datetime)
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.kotlinx.collections.immutable)

			implementation(libs.subsonicKotlin)
			implementation(libs.sentryKmp)
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
		}

		androidMain.dependencies {
			implementation(libs.bundles.ktor.android)
			implementation(libs.bundles.androidx.android)
			implementation(libs.bundles.media3)
			implementation(libs.androidx.work)
			implementation(libs.androidx.sqlite.bundled)
			implementation(libs.coil.gif)
			implementation(libs.kmpalette.core)
		}

		iosMain.dependencies {
			implementation(libs.bundles.ktor.ios)
			implementation(libs.androidx.sqlite.bundled)
			implementation(libs.coil.gif)
			implementation(libs.kmpalette.core)
		}

		wasmJsMain.dependencies {
			implementation(libs.androidx.sqlite.web.wasm.js)
			implementation(libs.kotlinx.browser)
		}

		val desktopMain by getting {
			dependencies {
				implementation(libs.sqlite.jdbc)
				implementation(libs.kmpalette.core)
				implementation(libs.mp3spi)
				implementation(libs.nucleus.updater.runtime)
				implementation(libs.nucleus.media.control)
				implementation(libs.nucleus.system.color)
			}
		}
	}

	compilerOptions {
		freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xexplicit-backing-fields")
		// material3 1.12 still marks these experimental (1.13 stabilised most of them)
		optIn.addAll(
			"androidx.compose.material3.ExperimentalMaterial3Api",
			"androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
		)
	}
}

room3 {
	schemaDirectory("$projectDir/schemas")
}

dependencies {
	add("kspAndroid", libs.androidx.room3.compiler)
	add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
	add("kspIosArm64", libs.androidx.room3.compiler)
	add("kspWasmJs", libs.androidx.room3.compiler)
	add("kspDesktop", libs.androidx.room3.compiler)

	add("kspCommonMainMetadata", libs.androidx.room3.compiler)
}
