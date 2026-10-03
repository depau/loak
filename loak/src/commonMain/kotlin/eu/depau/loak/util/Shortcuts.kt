package eu.depau.loak.util

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed

/** macOS, iOS and iPadOS: keyboard shortcuts take ⌘ instead of Ctrl. */
expect val isApplePlatform: Boolean

/** Ctrl, or ⌘ on Apple platforms: the modifier of the app's keyboard shortcuts. */
val KeyEvent.isShortcutPressed: Boolean
	get() = if (isApplePlatform) isMetaPressed else isCtrlPressed

/** A tooltip naming its shortcut: "Search (Ctrl+F)", "Search (⌘F)". */
fun withShortcut(label: String, key: String) =
	"$label (${if (isApplePlatform) "⌘$key" else "Ctrl+$key"})"
