package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.settings.AppIconVariant

actual class AppIconManager {
	actual fun setVariant(newVariant: AppIconVariant) {}
	actual fun getIcon(variant: AppIconVariant): Any? = null
}
