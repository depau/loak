package eu.depau.loak.domain.manager

@JsFun("(url) => window.open(url, '_blank', 'noopener')")
private external fun openInNewTab(url: String): Unit

/**
 * Web: opens links in a new browser tab.
 */
actual class LinkManager {
	actual fun openLink(link: String) {
		openInNewTab(link)
	}
}
