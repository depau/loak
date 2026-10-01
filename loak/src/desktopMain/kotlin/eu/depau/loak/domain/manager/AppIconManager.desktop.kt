package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.settings.AppIconVariant

/**
 * Desktop: the OS launcher manages app icons; a JVM app can't swap them.
 * No-op, like web.
 */
actual class AppIconManager {
	actual fun setVariant(newVariant: AppIconVariant) {}
	actual fun getIcon(variant: AppIconVariant): Any? = null
}
