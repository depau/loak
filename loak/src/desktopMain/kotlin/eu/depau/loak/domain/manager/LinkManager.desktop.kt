package eu.depau.loak.domain.manager

import java.awt.Desktop
import java.net.URI

/**
 * Desktop: open links in the platform browser.
 */
actual class LinkManager {
	actual fun openLink(link: String) {
		if (Desktop.isDesktopSupported()) {
			Desktop.getDesktop().browse(URI(link))
		}
	}
}
