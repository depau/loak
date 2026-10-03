package eu.depau.loak.util

actual val isApplePlatform = System.getProperty("os.name").startsWith("Mac")
