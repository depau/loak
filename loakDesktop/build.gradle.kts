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
				implementation(libs.nucleus.application)
				implementation(libs.nucleus.decorated.window.tao)
			}
		}
	}
}

compose.desktop {
	application {
		mainClass = "eu.depau.loak.desktopapp.MainKt"
		// Nucleus' Tao window backend must own the macOS main thread (its Gradle
		// plugin would add this; we only use the runtime libraries). Installers
		// are built per host OS, so checking the build host is enough.
		if (System.getProperty("os.name").startsWith("Mac")) {
			jvmArgs("-XstartOnFirstThread")
		}

		nativeDistributions {
			targetFormats(
				TargetFormat.Dmg,
				TargetFormat.Msi,
				TargetFormat.Exe,
				TargetFormat.Deb,
				TargetFormat.Rpm
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
				// Compiled from the iOS Icon Composer icon (needs Xcode 26+):
				//   xcrun actool "$PWD/iosApp/iosApp/AppIcon.icon" --compile "$PWD/loakDesktop/icons/macos" \
				//     --platform macosx --minimum-deployment-target 11.0 --app-icon AppIcon \
				//     --output-partial-info-plist /tmp/partial.plist
				// AppIcon.icns is the pre-rendered fallback for macOS < 26; macOS 26 draws the
				// Liquid Glass icon from Assets.car, found through CFBundleIconName.
				iconFile.set(project.file("icons/macos/AppIcon.icns"))
				infoPlist {
					extraKeysRawXml = "<key>CFBundleIconName</key><string>AppIcon</string>"
				}
			}
		}
	}
}

// jpackage can't add files to Contents/Resources: put Assets.car into the app image (which
// packageDmg reuses) and re-seal its ad-hoc signature.
tasks.matching { it.name == "createDistributable" }.configureEach {
	// locals, not script vals: the configuration cache can't capture the script
	val assetsCar = file("icons/macos/Assets.car")
	val appBundle = layout.buildDirectory.dir("compose/binaries/main/app/Loak.app")
	inputs.file(assetsCar)
	doLast {
		if (!System.getProperty("os.name").startsWith("Mac")) return@doLast
		val app = appBundle.get().asFile
		assetsCar.copyTo(app.resolve("Contents/Resources/Assets.car"), overwrite = true)
		val codesign = ProcessBuilder(
			"codesign", "--force", "--sign", "-",
			"--preserve-metadata=entitlements,requirements,flags,runtime", app.path
		).inheritIO().start()
		check(codesign.waitFor() == 0) { "codesign failed" }
	}
}
