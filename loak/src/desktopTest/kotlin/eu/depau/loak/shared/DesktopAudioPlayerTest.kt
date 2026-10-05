package eu.depau.loak.shared

import java.lang.reflect.Proxy
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAudioPlayerTest {
	@Test
	fun resumeRequiresAnOpenAudioLine() {
		// Exercise the private backend without opening a real device or network stream.
		val type = Class.forName(
			"eu.depau.loak.shared.DesktopMediaPlayerViewModel\$DesktopAudioPlayer"
		)
		val player = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
		val resume = type.getDeclaredMethod("resume").apply { isAccessible = true }
		assertEquals(false, resume.invoke(player))

		var started = false
		val line = Proxy.newProxyInstance(
			SourceDataLine::class.java.classLoader,
			arrayOf(SourceDataLine::class.java)
		) { _, method, _ ->
			if (method.name == "start") started = true
			null
		}
		type.getDeclaredField("line").apply { isAccessible = true }.set(player, line)
		type.getDeclaredField("paused").apply { isAccessible = true }.setBoolean(player, true)
		assertEquals(true, resume.invoke(player))
		assertTrue(started)
		assertEquals(false, type.getDeclaredField("paused").apply {
			isAccessible = true
		}.getBoolean(player))
	}
}
