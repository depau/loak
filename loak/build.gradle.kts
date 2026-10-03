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

val generateBuildInfo = tasks.register("generateBuildInfo", Sync::class) {
	description = "generate BuildInfo.kt"

	from(
		resources.text.fromString(
			"""
			|package eu.depau.loak.generated
			|
			|// BuildInfo carries no flags — update checks are on for every build.
			|
			""".trimMargin()
		)
	) {
		rename { "BuildInfo.kt" }
		into("eu/depau/loak/generated")
	}

	into(layout.buildDirectory.dir("generated/buildInfo/commonMain/kotlin"))
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
	dependsOn("generateValkyrieImageVector")
	dependsOn(generateBuildInfo)
}

// no idea why ksp tasks depend on valkyrie
tasks.withType<KspAATask>().configureEach {
	dependsOn("generateValkyrieImageVector")
}

kotlin.sourceSets.commonMain {
	kotlin.srcDir(generateBuildInfo.map { it.destinationDir })
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
			implementation(libs.androidx.sqlite.bundled)
			implementation(libs.coil.gif)
			implementation(libs.kmpalette.core)
			implementation(libs.kmpalette.network)
		}

		iosMain.dependencies {
			implementation(libs.bundles.ktor.ios)
			implementation(libs.androidx.sqlite.bundled)
			implementation(libs.coil.gif)
			implementation(libs.kmpalette.core)
			implementation(libs.kmpalette.network)
		}

		wasmJsMain.dependencies {
			implementation(libs.androidx.sqlite.web.wasm.js)
			implementation(libs.kotlinx.browser)
		}

		val desktopMain by getting {
			dependencies {
				implementation(libs.androidx.sqlite.bundled)
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
