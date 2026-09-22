-dontobfuscate

-keepattributes SourceFile, LineNumberTable
-keepattributes *Annotation*

-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keepnames class eu.depau.loak.**,dev.zt64.** { *; }
