import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.nucleus)
}

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
			TargetFormat.Msi,
			TargetFormat.Exe,
			TargetFormat.Deb,
			TargetFormat.Rpm,
			TargetFormat.AppImage
		)
		packageName = "Loak"
		packageVersion = "1.0.0"
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
			// the 256px rendering of icons/macos/AppIcon.icns (iconutil -c iconset); Linux
			// desktops don't shape app icons, so it carries its own rounded square
			iconFile.set(project.file("icons/loak.png"))
		}
		windows {
			iconFile.set(project.file("icons/loak.ico"))
			menuGroup = "Lo'ak"
		}
		macOS {
			bundleID = "eu.depau.loak.desktopapp"
			// AppIcon.icns is the pre-rendered fallback for macOS < 26; on 26 the plugin compiles
			// the Icon Composer icon into Assets.car with actool (Xcode 26+)
			iconFile.set(project.file("icons/macos/AppIcon.icns"))
			layeredIconDir.set(rootProject.layout.projectDirectory.dir("iosApp/iosApp/AppIcon.icon"))
		}
	}
}
