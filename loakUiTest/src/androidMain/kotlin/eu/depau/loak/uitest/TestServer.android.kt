package eu.depau.loak.uitest

import androidx.test.platform.app.InstrumentationRegistry

internal actual val testServerPort: Int? =
	InstrumentationRegistry.getArguments().getString("loakUitestPort")?.toIntOrNull()
