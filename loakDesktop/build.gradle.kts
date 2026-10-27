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
// `packageVersion` is the semantic app version the Nucleus updater compares
// against its release manifest. jpackage only accepts numeric versions, so the
// native package uses `numericVersion` while a JVM property restores the full
// semantic version at runtime. CI nightlies are already numeric `1.0.<run>`.
// `updateChannel` is baked into the JVM options so the packaged app can select
// the fixed nightly release instead of the stable GitHub release stream.
//
// Strict installer metadata and artifact names also use the derived numeric
// version. The updater reads the manifest version and `nucleus.app.version`,
// never the installer filename.
// ---------------------------------------------------------------------------
val releaseVersion: String = project.findProperty("packageVersion") as String? ?: "1.0.0"
val updateChannel: String = project.findProperty("updateChannel") as String? ?: "release"

/** Converts release and nightly versions to the numeric MAJOR.MINOR.BUILD installers require. */
fun numericBuildVersion(version: String): String {
	val base = version.substringBefore('-').split('.')
	val major = base.getOrNull(0)?.toIntOrNull() ?: 1
	val minor = base.getOrNull(1)?.toIntOrNull() ?: 0
	val patch = base.getOrNull(2)?.toIntOrNull() ?: 0
	val prereleaseBuild = version.substringAfter('-', "").filter(Char::isDigit).toIntOrNull()
	val build = if (patch == 0) prereleaseBuild ?: 0 else patch
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
				implementation(libs.nucleus.menu.macos)
				implementation(libs.cmp.resources)
				implementation(libs.androidx.navigation3.ui) // the back input for trackpad swipes
			}
		}
		val desktopTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(project(":loakUiTest"))
				implementation(libs.nucleus.rodio) // to tell whether the machine has audio output
				implementation(libs.androidx.room3.runtime)
				implementation(libs.bundles.androidx.lifecycle)
				implementation(libs.cmp.ui.test)
				implementation(libs.cmp.ui.test.junit4)
				implementation(libs.cmp.material3.windowSize)
				implementation(libs.ktor.client.mock)
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.subsonicKotlin)
				implementation(libs.kotlinx.serialization.json)
				implementation(libs.androidx.navigation3.ui)
				implementation(libs.multiplatformSettings)
			}
		}
	}
}

tasks.named<Test>("desktopTest") {
	// the UI flows run the real app, which keeps settings and databases under the
	// user's home: give the test JVM a throwaway one so tests never touch real data
	val home = layout.buildDirectory.dir("test-home").get().asFile
	doFirst { home.deleteRecursively(); home.mkdirs() }
	systemProperty("user.home", home.absolutePath)
	systemProperty("loak.uitest.failures", layout.buildDirectory.dir("ui-test-failures").get().asFile.absolutePath)
	usesNavidrome(this)
}

// Nucleus native libraries rebuilt with Lo'ak's patches (nucleus/), built on first use (needs
// cargo) and swapped into Nucleus' jars for the app, its packages and its UI tests alike.
val nucleusRodio by tasks.registering(BuildPatchedNative::class) {
	// the bridge decodes ahead of the output's real-time thread, which otherwise blocked on disk
	// and network reads and dropped a cycle (a click) each time one took too long
	source = GitSource(
		"https://github.com/NucleusFramework/AudioKt.git",
		"v${libs.versions.nucleus.rodio.get()}",
		fileTree("nucleus/audiokt"),
		path = "rodio/src/main/native",
		tests = true,
	)
	library = "nucleus_rodio"
	// rodio's resampler restarted on every decoded packet, clicking at each one whenever the
	// song's rate isn't the output's (a 44.1 kHz song on a 48 kHz device); v0.21.1 is the
	// version AudioKt's Cargo.lock pins
	patchedCrates.put(
		"rodio",
		GitSource(
			"https://github.com/RustAudio/rodio.git",
			"v0.21.1",
			fileTree("nucleus/rodio"),
			tests = true,
		),
	)
	outputDir = layout.buildDirectory.dir("nucleus-natives/rodio")
}
// touchpad scroll phases on Wayland, which the trackpad swipes need
val nucleusTao = if (HostPlatform.current.isLinux) {
	tasks.register<BuildPatchedNative>("nucleusTao") {
		source = GitSource(
			"https://github.com/NucleusFramework/Nucleus.git",
			"v${libs.versions.nucleus.asProvider().get()}",
			fileTree("nucleus/tao"),
			path = "decorated-window-tao/src/main/native",
		)
		library = "nucleus_tao"
		outputDir = layout.buildDirectory.dir("nucleus-natives/tao")
	}
} else {
	null
}
swapNucleusNatives(
	files(listOfNotNull(nucleusRodio, nucleusTao)),
	"desktopRuntimeClasspath",
	"desktopTestRuntimeClasspath",
)

nucleus.application {
	mainClass = "eu.depau.loak.desktopapp.MainKt"
	jvmArgs("-Dnucleus.app.version=$releaseVersion", "-Dloak.updateChannel=$updateChannel")

	nativeDistributions {
		// one package task per format (packageDmg, packageAppImage, ...); electron-builder
		// builds them, on a Node.js the plugin downloads
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
		// jpackage requires this numeric value; nucleus.app.version above keeps
		// prerelease identifiers for updater comparisons.
		packageVersion = numericVersion
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
