package eu.depau.loak.uitest

actual val testServerHost = "127.0.0.1"

internal actual val testServerPort: Int? = System.getProperty("loak.uitest.port")?.toIntOrNull()
