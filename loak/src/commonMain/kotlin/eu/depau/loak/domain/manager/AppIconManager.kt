package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.settings.AppIconVariant

expect class AppIconManager {
	fun setVariant(newVariant: AppIconVariant)
	fun getIcon(variant: AppIconVariant): Any?
}
