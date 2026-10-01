package eu.depau.loak.di

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

interface PlatformContext {
	val name: String
	val appVersion: String
	val colorScheme: ColorScheme?
	val sizeClass: WindowSizeClass
	val platformType: PlatformType
}

@Composable
fun PlatformContext.isLandscape() = remember(sizeClass) {
	sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
}

/** Landscape tablets, desktop and web windows: the full player bar replaces the mini player. */
@Composable
fun PlatformContext.isExpanded() = remember(sizeClass) {
	sizeClass.widthSizeClass >= WindowWidthSizeClass.Expanded
}

enum class PlatformType {
	Android,
	IOS,
	Desktop,
	Web
}

@Composable
expect fun rememberPlatformContext(): PlatformContext
