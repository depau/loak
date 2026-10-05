package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainSong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS: writes the original to a temp file, then hands it to the share sheet so the user can
 * pick "Save to Files" (or any share target). The sheet must be presented on the main queue.
 */
@OptIn(ExperimentalForeignApi::class)
actual class ExportManager {
	actual suspend fun prepareTarget(song: DomainSong, fileName: String): ExportTarget? {
		val path = NSTemporaryDirectory() + fileName
		return ExportTarget(path)
	}

	actual suspend fun openSink(target: ExportTarget): Sink =
		SystemFileSystem.sink(Path(target.path)).buffered()

	actual suspend fun commit(target: ExportTarget, sink: Sink, success: Boolean) {
		sink.close()
		if (!success) {
			SystemFileSystem.delete(Path(target.path), mustExist = false)
			return
		}
		dispatch_async(dispatch_get_main_queue()) {
			val url = NSURL.fileURLWithPath(target.path)
			share(url)
		}
	}

	private fun getTopVC(): UIViewController? {
		val window = UIApplication.sharedApplication.connectedScenes
			.filterIsInstance<UIWindowScene>()
			.flatMap { it.windows }
			.filterIsInstance<UIWindow>()
			.firstOrNull { it.isKeyWindow() }
			?: return null
		var rootViewController = window.rootViewController
		while (rootViewController?.presentedViewController != null) {
			rootViewController = rootViewController.presentedViewController
		}
		return rootViewController
	}

	private fun share(activityItem: Any) {
		val rootViewController = getTopVC()
		val activityViewController = UIActivityViewController(listOf(activityItem), null)

		// will crash on iPadOS if you don't do this
		if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
			activityViewController.popoverPresentationController?.sourceView =
				rootViewController?.view
		}

		rootViewController?.presentViewController(activityViewController, true, null)
	}
}

actual class ExportTarget(val path: String)
