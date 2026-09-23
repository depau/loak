package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.settings.AppIconVariant

/**
 * Web: app icons are managed by the OS launcher, which a browser tab can't
 * change. No-op.
 */
actual class AppIconManager {
	actual fun setVariant(newVariant: AppIconVariant) {}
	actual fun getIcon(variant: AppIconVariant): Any? = null
}
