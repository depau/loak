package eu.depau.loak.domain.models.settings

enum class AppIconVariant(
	val activityName: String,
	val designer: String
) {
	Default("MainActivityDefault", designer = "ssalggnikool"),
	Inverted("MainActivityInverted", designer = "ssalggnikool")
}
