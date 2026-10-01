package eu.depau.loak.util

import platform.UIKit.UIDevice

// ponytail: iOS 16+ reports only the model ("iPhone") here; the setting covers the rest
actual fun systemDeviceName(): String = UIDevice.currentDevice.name
