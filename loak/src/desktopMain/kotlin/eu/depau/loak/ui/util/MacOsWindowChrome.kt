package eu.depau.loak.ui.util

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import eu.depau.loak.util.isApplePlatform

/** NSWindowStyleMaskFullSizeContentView. */
private const val FullSizeContentView = 1L shl 15

/**
 * Turns Nucleus' client-side chrome (content under a transparent title bar, traffic lights
 * drawn over it) on or off. macOS draws no title bar of its own in full screen for such a
 * window, and Nucleus' replacement traffic lights sit over the content there, so full screen
 * goes without: the content then starts below the hidden title bar and fills the screen.
 */
// ponytail: AppKit poke under Nucleus; drop once Nucleus can turn its full-screen controls off
fun setMacOsClientSideDecorations(nsViewHandle: Long, enabled: Boolean) {
	if (!isApplePlatform || nsViewHandle == 0L) return
	val window = send(Pointer(nsViewHandle), "window")
	if (Pointer.nativeValue(window) == 0L) return
	val styleMask = Pointer.nativeValue(send(window, "styleMask"))
	val newMask =
		if (enabled) styleMask or FullSizeContentView else styleMask and FullSizeContentView.inv()
	if (newMask == styleMask) return
	messageSend.invokeVoid(arrayOf(window, selector("setStyleMask:"), newMask))
}

private val objectiveC by lazy { NativeLibrary.getInstance("objc") }
private val messageSend by lazy { objectiveC.getFunction("objc_msgSend") }
private val registerSelector by lazy { objectiveC.getFunction("sel_registerName") }

private fun selector(name: String): Pointer = registerSelector.invokePointer(arrayOf(name))

private fun send(receiver: Pointer, selectorName: String): Pointer =
	messageSend.invokePointer(arrayOf(receiver, selector(selectorName)))
