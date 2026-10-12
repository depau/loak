import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.UUID

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

val isTaskRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

val nightly = System.getenv("NIGHTLY") == "true" || providers.gradleProperty("nightly")
	.map { it.toBoolean() }
	.getOrElse(false)

// Optional version overrides, used by the nightly build. Falls back to the hardcoded release
// versions when absent (Gradle also allows `-PversionCode=… -PversionName=…` from the CLI).
val versionCodeOverride = providers.gradleProperty("versionCode").map { it.toInt() }.orNull
val versionNameOverride = providers.gradleProperty("versionName").orNull

extensions.configure<ApplicationExtension> {
	namespace = "eu.depau.loak.androidApp"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	buildFeatures {
		resValues = true
	}

	defaultConfig {
		applicationId = "eu.depau.loak"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = versionCodeOverride ?: 58
		versionName = versionNameOverride ?: "v1.0.0-alpha58"

		if (nightly) {
			applicationIdSuffix = ".nightly"
			resValue("string", "app_name", "Loak (Nightly)")
		}

		ndk {
			abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
			if (!isTaskRelease) {
				abiFilters.add("x86_64")
			}
		}
	}

	signingConfigs {
		create("release") {
			keyAlias = System.getenv("SIGNING_KEY_ALIAS")
			keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
			storeFile = System.getenv("SIGNING_STORE_FILE")?.let(::File)
			storePassword = System.getenv("SIGNING_STORE_PASSWORD")
		}
	}

	buildTypes {
		getByName("release") {
			isMinifyEnabled = true
			isDebuggable = false
			isProfileable = false
			isJniDebuggable = false
			isShrinkResources = true
			signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}

		getByName("debug") {
			applicationIdSuffix = ".debug"
			resValue("string", "app_name", "Loak (Dev)")
		}
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

		jniLibs {
			keepDebugSymbols.add("**/*.so")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	dependenciesInfo {
		includeInApk = false
		includeInBundle = false
	}
}

extensions.configure<ApplicationAndroidComponentsExtension> {
	onVariants { variant ->
		variant.outputs.forEach { output ->
			if (output is VariantOutputImpl) {
				output.outputFileName = when {
					nightly && !isTaskRelease -> "Lo'ak.nightly-debug.apk"
					nightly -> "Lo'ak.nightly.apk"
					else -> "Lo'ak.apk"
				}
			}
		}
	}
	onVariants(selector().withBuildType("release")) {
		it.packaging.resources.excludes.apply {
			add("/**/*.version")
			add("/kotlin-tooling-metadata.json")
			add("/DebugProbesKt.bin")
			add("/**/*.kotlin_builtins")
		}
		val meta = tasks.register<SentryDebugMeta>("generate${it.name.replaceFirstChar(Char::uppercase)}SentryDebugMeta") {
			uuid.set(it.applicationId.zip(providers.environmentVariable("GITHUB_SHA").orElse("local")) { id, sha ->
				UUID.nameUUIDFromBytes("$sha:$id".toByteArray()).toString()
			})
		}
		it.sources.assets?.addGeneratedSourceDirectory(meta, SentryDebugMeta::outputDir)
	}
}

/**
 * Lets Sentry deobfuscate release stack traces. Names survive (-dontobfuscate), but R8 still
 * inlines, merges classes and renumbers lines, so traces need this build's mapping.txt. The SDK
 * sends the UUID from this asset with every event; CI uploads mapping.txt under the same UUID
 * (see .github/workflows/build.yml). Derived from commit + app id: one per CI build kind.
 */
abstract class SentryDebugMeta : DefaultTask() {
	@get:Input
	abstract val uuid: Property<String>

	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty

	@TaskAction
	fun write() {
		outputDir.get().file("sentry-debug-meta.properties").asFile
			.writeText("io.sentry.ProguardUuids=${uuid.get()}\n")
	}
}

dependencies {
	implementation(projects.loak)
	implementation(libs.androidx.activity.compose)
	implementation(libs.cmp.material3)
	implementation(libs.koin.android)
	implementation(libs.koin.core)
	implementation(libs.bundles.glance)
	implementation(libs.bundles.coil)
	implementation(libs.bundles.media3)
}
