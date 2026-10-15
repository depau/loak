package eu.depau.loak.uitest

import androidx.test.platform.app.InstrumentationRegistry

// the emulator's alias for the host's loopback, where the fixture server listens
actual val testServerHost = "10.0.2.2"

internal actual val testServerPort: Int? =
	InstrumentationRegistry.getArguments().getString("loakUitestPort")?.toIntOrNull()
