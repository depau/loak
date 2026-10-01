import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
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
			}
		}
	}
}

compose.desktop {
	application {
		mainClass = "eu.depau.loak.desktopapp.MainKt"

		nativeDistributions {
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
				appCategory = "Audio"
				iconFile.set(project.file("icons/loak.png"))
			}
			windows {
				iconFile.set(project.file("icons/loak.ico"))
				menuGroup = "Lo'ak"
			}
			macOS {
				// jpackage builds the .icns from a PNG via iconutil on macOS.
				iconFile.set(project.file("icons/loak.png"))
			}
		}
	}
}
