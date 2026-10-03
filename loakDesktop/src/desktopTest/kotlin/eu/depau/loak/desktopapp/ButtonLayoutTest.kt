package eu.depau.loak.desktopapp

import dev.nucleusframework.window.utils.linux.LinuxTitleBarButton.CLOSE
import dev.nucleusframework.window.utils.linux.LinuxTitleBarButton.MAXIMIZE
import dev.nucleusframework.window.utils.linux.LinuxTitleBarButton.MINIMIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ButtonLayoutTest {
	@Test
	fun parsesBothSides() {
		assertEquals(listOf(CLOSE) to listOf(MINIMIZE, MAXIMIZE), parseButtonLayout("close:minimize,maximize"))
		assertEquals(emptyList<Nothing>() to listOf(MINIMIZE, MAXIMIZE, CLOSE), parseButtonLayout("appmenu:minimize,maximize,close"))
		assertEquals(listOf(CLOSE, MINIMIZE, MAXIMIZE) to emptyList(), parseButtonLayout("close,minimize,maximize:"))
		// no colon: everything sits on the left
		assertEquals(listOf(CLOSE) to emptyList(), parseButtonLayout("icon,close"))
		assertNull(parseButtonLayout("appmenu:"))
	}
}
