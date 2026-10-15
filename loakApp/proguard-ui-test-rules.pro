# Only for the `minified` build type the user flows run against. The test APK
# reuses the libraries the app ships (Kotlin, coroutines, Compose, AndroidX), so
# R8 must keep those whole; the app's own code and its other libraries (Ktor,
# Koin, serialization, Room, Media3…) stay shrunk exactly as in release.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.** { *; }
