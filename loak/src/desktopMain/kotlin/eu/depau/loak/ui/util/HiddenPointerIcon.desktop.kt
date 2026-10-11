package eu.depau.loak.ui.util

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

actual val HiddenPointerIcon: PointerIcon by lazy {
	PointerIcon(
		Toolkit.getDefaultToolkit()
			.createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(), "hidden")
	)
}
