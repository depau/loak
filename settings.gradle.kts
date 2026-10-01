@file:Suppress("UnstableApiUsage")

rootProject.name = "LoakMusic"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
	repositories {
		google {
			mavenContent {
				includeGroupAndSubgroups("androidx")
				includeGroupAndSubgroups("com.android")
				includeGroupAndSubgroups("com.google")
			}
		}
		maven {
			// JetBrains compose dev channel: hosts 1.13.0-alphaNN+devXXXX snapshots that
			// carry the verified SKIKO-1183 / CMP-10732 fix (alpha02 is not on Central yet).
			url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
			content {
				includeGroupAndSubgroups("org.jetbrains.compose")
				includeGroupAndSubgroups("org.jetbrains.compose.material3")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositories {
		google {
			mavenContent {
				includeGroupAndSubgroups("androidx")
				includeGroupAndSubgroups("com.android")
				includeGroupAndSubgroups("com.google")
			}
		}
		maven {
			// JetBrains compose dev channel (see pluginManagement above).
			url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
			content {
				includeGroupAndSubgroups("org.jetbrains.compose")
				includeGroupAndSubgroups("org.jetbrains.compose.material3")
				includeGroupAndSubgroups("org.jetbrains.skiko")
			}
		}
		maven {
			url = uri("https://raw.githubusercontent.com/Nightdavisao/maven-repo/refs/heads/main/")
		}
		mavenCentral()
	}
}

include(":loak")
include(":loakApp")
include(":webApp")
include(":loakDesktop")
