package eu.depau.loak.util

import android.os.Build

actual fun systemDeviceName(): String = Build.MODEL
