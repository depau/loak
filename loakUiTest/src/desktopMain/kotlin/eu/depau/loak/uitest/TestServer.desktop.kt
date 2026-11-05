package eu.depau.loak.uitest

internal actual val testServerPort: Int? = System.getProperty("loak.uitest.port")?.toIntOrNull()
