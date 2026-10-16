package eu.depau.loak.ui.util

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import eu.depau.loak.util.isApplePlatform

/**
 * Shows or hides the traffic lights Nucleus draws in full screen (its own `NucleusTaoButtonsView`,
 * standing in for AppKit's, which it hides): they float over the content, so immersive content
 * (karaoke) hides them, while a plain full-screen window keeps them as its way out.
 */
// ponytail: AppKit poke under Nucleus; drop once Nucleus can hide its full-screen controls
fun setMacOsFullscreenControlsHidden(nsViewHandle: Long, hidden: Boolean) {
	if (!isApplePlatform || nsViewHandle == 0L) return
	val window = send(Pointer(nsViewHandle), "window").orNull() ?: return
	val content = send(window, "contentView").orNull() ?: return
	val root = send(content, "superview").orNull() ?: content
	forEachView(root) { view ->
		if (className.invokeString(arrayOf(view), false) == "NucleusTaoButtonsView") {
			messageSend.invokeVoid(arrayOf(view, selector("setHidden:"), hidden))
		}
	}
}

private fun forEachView(view: Pointer, action: (Pointer) -> Unit) {
	action(view)
	val subviews = send(view, "subviews").orNull() ?: return
	val count = messageSend.invokeLong(arrayOf(subviews, selector("count")))
	for (i in 0 until count) {
		val child = messageSend.invokePointer(arrayOf(subviews, selector("objectAtIndex:"), i))
		child.orNull()?.let { forEachView(it, action) }
	}
}

private val objectiveC by lazy { NativeLibrary.getInstance("objc") }
private val messageSend by lazy { objectiveC.getFunction("objc_msgSend") }
private val registerSelector by lazy { objectiveC.getFunction("sel_registerName") }
private val className by lazy { objectiveC.getFunction("object_getClassName") }

private fun Pointer?.orNull(): Pointer? = this?.takeIf { Pointer.nativeValue(it) != 0L }

private fun selector(name: String): Pointer = registerSelector.invokePointer(arrayOf(name))

private fun send(receiver: Pointer, selectorName: String): Pointer? =
	messageSend.invokePointer(arrayOf(receiver, selector(selectorName)))
