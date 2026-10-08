package eu.depau.loak.shared

import java.lang.reflect.Proxy
import java.net.URISyntaxException
import java.net.URL
import java.io.File
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

	@Test
	fun downloadedFileUriRoundTripsThroughPlaybackUriParsing() {
		// Sanity: the regression input used to be a broken URL (breaks on the space
		// in "Application Support" and the %40 from the URL-encoded song id). This is
		// the exact path/error from Sentry LOAK-10/LOAK-Z.
		val broken = "file:///Users/ddepau/Library/Application Support/Loak/audio/" +
			"2cc83d4e.3nQq7uX26aa4fspqzIoR6f.mp3%400.mp3"
		assertFailsWith(URISyntaxException::class) { URL(broken).toURI() }

		// What resolveSourceUrl now builds: File.toURI() encodes both characters.
		val path = "/Users/ddepau/Library/Application Support/Loak/audio/" +
			"2cc83d4e.3nQq7uX26aa4fspqzIoR6f.mp3%400.mp3"
		val uri = File(path).toURI().toString()
		// playUrl decodes it back to the exact on-disk file.
		assertEquals(File(URL(uri).toURI()).path, path)
	}

	@Test
	fun downloadedFileUriIsEncoded() {
		// Spaces and literal %NN in the on-disk name must survive a URL -> URI
		// round trip: the path playUrl reads back must be the original one.
		val path = "/tmp/Library/Application Support/audio/song%40x.mp3"
		val uri = File(path).toURI().toString()
		assertTrue(uri.contains("%20")) // space is always percent-encoded
		// the raw '%' in the name is re-encoded as %25, so the URI never carries a
		// bare %40 (that's exactly what made URL(...).toURI() explode before)
		assertTrue(!uri.contains("%40"))
		assertEquals(File(URL(uri).toURI()).path, path)
	}
}
