import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.nucleus)
}

// ---------------------------------------------------------------------------
// Version plumbing for auto-update.
//
// `packageVersion` becomes the app version the Nucleus updater compares against
// the release manifest (nucleus.app.version), so it MUST be the full release
// version, including the prerelease suffix for alpha tags: v1.0.0-alpha58 ->
// "1.0.0-alpha58". CI passes it as -PpackageVersion; local/manual builds keep
// the plain "1.0.0" default (which matches no published release, so the updater
// never announces an update from a dev build).
//
// The strict installers (MSI/EXE want MAJOR.MINOR.BUILD, RPM forbids '-', DEB
// wants a plain numeric upstream version) get their own derived numeric version
// for artifact naming and OS package metadata; the updater only ever looks at
// the manifest `version` vs `nucleus.app.version`, never at a filename.
// ---------------------------------------------------------------------------
val releaseVersion: String = project.findProperty("packageVersion") as String? ?: "1.0.0"

/** Strips any prerelease/tag to a numeric MAJOR.MINOR.BUILD (1.0.0-alpha58 -> 1.0.58). */
fun numericBuildVersion(version: String): String {
	val parts = version.split('.')
	val major = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 1
	val minor = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
	val build = parts.drop(2).joinToString("-").filter { it.isDigit() }.toIntOrNull() ?: 0
	return "$major.$minor.$build"
}

val numericVersion = numericBuildVersion(releaseVersion)

kotlin {
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	sourceSets {
		val desktopMain by getting {
			dependencies {
				implementation(project(":loak"))
				implementation(compose.runtime)
				implementation(compose.foundation)
				implementation(compose.desktop.currentOs)
				implementation(compose.material3)
				implementation(libs.koin.core)
				implementation(libs.ktor.client.okhttp)
				implementation(libs.kotlinx.coroutines.swing)
				implementation(libs.nucleus.application)
				implementation(libs.nucleus.updater.runtime)
				implementation(libs.nucleus.decorated.window.tao)
				implementation(libs.nucleus.darkmode.detector)
			}
		}
	}
}

nucleus.application {
	mainClass = "eu.depau.loak.desktopapp.MainKt"
	// Nucleus' Tao window backend must own the macOS main thread; the plugin only adds this
	// to hot reload. Installers are built per host OS, so checking the build host is enough.
	if (System.getProperty("os.name").startsWith("Mac")) {
		jvmArgs("-XstartOnFirstThread")
	}

	nativeDistributions {
		// one package task per format (packageDmg, packageAppImage, ...); electron-builder
		// builds them, so packaging needs Node.js >= 20.19 (the plugin only checks for 18)
		targetFormats(
			TargetFormat.Dmg,
			TargetFormat.Zip,
			TargetFormat.Msi,
			TargetFormat.Exe,
			TargetFormat.Deb,
			TargetFormat.Rpm,
			TargetFormat.AppImage
		)
		packageName = "Loak"
		// Full release version (tag-derived in CI): this is what the app reports
		// and what the auto-updater compares against the manifest. The
		// --strict-installer numeric overrides below only shape package metadata
		// and file names; nucleus.app.version keeps this full value.
		packageVersion = releaseVersion
		description = "Lo'ak — a modern Subsonic music client"
		// electron-builder refuses a .deb without these
		homepage = "https://github.com/depau/loak"
		vendor = "Davide Depau"
		// jlink builds a minimal per-OS runtime bundle; GraalVM native-image
		// is a DESIGN_CHANGES backlog item. Kept lean: includeAllModules
		// would balloon the runtime to ~166 MB.
		modules(
			"java.desktop",
			"java.sql",
			"java.naming",
			"java.logging",
			"jdk.unsupported",
			"java.management"
		)

		linux {
			// freedesktop needs the AudioVideo main category next to Audio
			appCategory = "AudioVideo;Audio"
			debMaintainer = "Davide Depau <davide@depau.eu>"
			// the jpackage launcher is named after packageName, which sets the X11 WM_CLASS
			startupWMClass = "Loak"
			// Debian/RPM versions must be numeric: Debian rejects embedded dashes in the
			// upstream version and RPM forbids '-' entirely, both present in alpha tags.
			debPackageVersion = numericVersion
			rpmPackageVersion = numericVersion.replace('-', '.')
			// the 256px rendering of icons/macos/AppIcon.icns (iconutil -c iconset); Linux
			// desktops don't shape app icons, so it carries its own rounded square
			iconFile.set(project.file("icons/loak.png"))
		}
		windows {
			iconFile.set(project.file("icons/loak.ico"))
			menuGroup = "Lo'ak"
			// MSI/EXE are version-validated as MAJOR.MINOR.BUILD; alpha tags would
			// otherwise fail. The updater still gets the full version via
			// nucleus.app.version and matches a release by its numeric tag too.
			msiPackageVersion = numericVersion
			exePackageVersion = numericVersion
		}
		macOS {
			bundleID = "eu.depau.loak.desktopapp"
			// DMG is version-validated as MAJOR[.MINOR][.PATCH]; alpha tags would fail
			dmgPackageVersion = numericVersion
			// AppIcon.icns is the pre-rendered fallback for macOS < 26; on 26 the plugin compiles
			// the Icon Composer icon into Assets.car with actool (Xcode 26+)
			iconFile.set(project.file("icons/macos/AppIcon.icns"))
			layeredIconDir.set(rootProject.layout.projectDirectory.dir("iosApp/iosApp/AppIcon.icon"))
		}
	}
}
