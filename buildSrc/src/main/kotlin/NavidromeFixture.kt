import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * The real Subsonic server the UI flows run against: a pinned Navidrome on
 * 127.0.0.1 (port [DEFAULT_PORT], or -Ploak.uitest.port) serving the committed library in `loakUiTest/fixture/music`,
 * with one admin user. Started on first use by any test task that needs it
 * (Android test tasks forward it into the emulator, see [connectsDevicesToNavidrome]),
 * stopped when the build ends.
 * Every build starts from an empty Navidrome database.
 * A second instance, on the next port, serves a large library generated into the build
 * dir on first use ([generateLargeLibrary]).
 */
abstract class NavidromeFixture : BuildService<NavidromeFixture.Params>, AutoCloseable {
	interface Params : BuildServiceParameters {
		val musicDir: DirectoryProperty
		val workDir: DirectoryProperty
		val port: Property<Int>
		/** Where Navidrome itself is downloaded to, shared by both instances. */
		val binDir: DirectoryProperty
		/** Set for the large library: [musicDir] is generated from this MP3 on first use. */
		val generateFrom: RegularFileProperty
	}

	companion object {
		/** Builds running UI tests at the same time need their own: -Ploak.uitest.port=… */
		const val DEFAULT_PORT = 45330
		const val USER = "loak"
		const val PASSWORD = "loak-ui-test"

		private const val VERSION = "0.64.2"
		private val SHA256 = mapOf(
			"darwin_amd64.tar.gz" to "bc872847dcd1c0d760bb9b8730f2a3c876e0ff0679e5440755c37697f2804a23",
			"darwin_arm64.tar.gz" to "8b0a7798001453719ad50c12f7025a6044546c4a3568efe51feb296681f29956",
			"linux_amd64.tar.gz" to "fdd87fd107818667c2c50bc24dc6ce856962c795b3822a91f95c066cfad2c53e",
			"linux_arm64.tar.gz" to "6d1683428cb6d99cdabc3da815b94cc161de5904820130c5b9e2e42c34dcdb15",
			// no windows arm64 build: Windows on ARM runs the x64 one emulated
			"windows_amd64.zip" to "72e06c9101fca6b14ff1437f9a56beb7ad6fe4bd7f25ff62cd5b70a075fee305",
		)
	}

	private val port get() = parameters.port.get()
	private val base get() = "http://127.0.0.1:$port"
	private val auth = "u=$USER&p=$PASSWORD&v=1.16.1&c=loak-ui-test&f=json"
	private var process: Process? = null

	@Synchronized
	fun start() {
		if (process != null) return
		val work = parameters.workDir.get().asFile
		val music = parameters.musicDir.get().asFile
		val data = File(work, "data").apply { deleteRecursively(); mkdirs() }
		check(runCatching { get("/ping") }.isFailure) {
			"Port $port is already in use: is another build running UI tests? Pick another with -Ploak.uitest.port"
		}
		val started = System.nanoTime()
		parameters.generateFrom.orNull?.let { generateLargeLibrary(it.asFile, music) }
		process = ProcessBuilder(binary().absolutePath)
			.redirectErrorStream(true)
			.redirectOutput(File(work, "navidrome.log"))
			.apply {
				environment().putAll(
					mapOf(
						"ND_MUSICFOLDER" to music.absolutePath,
						"ND_DATAFOLDER" to data.absolutePath,
						"ND_PORT" to "$port",
						"ND_LOGLEVEL" to "warn",
						// hermetic: no telemetry, no Last.fm/Spotify/Deezer lookups
						"ND_ENABLEINSIGHTSCOLLECTOR" to "false",
						"ND_ENABLEEXTERNALSERVICES" to "false",
						// emulators reach it through adb reverse
						"ND_ADDRESS" to "127.0.0.1",
					)
				)
			}
			.start()
		waitFor("Navidrome to start") { runCatching { get("/ping") }.isSuccess }
		post("/auth/createAdmin", """{"username":"$USER","password":"$PASSWORD"}""")
		// creating the admin starts a scan; .m3u playlists are only imported once
		// an admin exists, so rescan after it
		waitForScan()
		get("/rest/startScan?fullScan=true&$auth")
		waitForScan()
		val songs = music.walk().count { it.extension == "mp3" }
		check(get("/rest/getScanStatus?$auth").contains("\"count\":$songs,")) {
			"Navidrome didn't index all $songs songs"
		}
		val playlists = get("/rest/getPlaylists?$auth")
		music.listFiles { it.extension == "m3u" }!!.forEach {
			check(playlists.contains("\"${it.nameWithoutExtension}\"")) { "fixture playlist ${it.name} not imported" }
		}
		val seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
		println("Navidrome fixture on port $port: $songs songs, ready in $seconds s")
	}

	override fun close() {
		process?.apply {
			destroy()
			if (!waitFor(10, TimeUnit.SECONDS)) destroyForcibly()
		}
	}

	private fun waitForScan() {
		Thread.sleep(500) // let a just-requested scan start before polling it
		waitFor("the library scan", 600) { get("/rest/getScanStatus?$auth").contains("\"scanning\":false") }
	}

	// both instances may want it at once
	private fun binary(): File = synchronized(NavidromeFixture::class) { download(parameters.binDir.get().asFile) }

	private fun download(work: File): File {
		val os = System.getProperty("os.name").lowercase()
		val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "amd64"
		val asset = when {
			os.contains("win") -> "windows_amd64.zip"
			os.contains("mac") -> "darwin_$arch.tar.gz"
			else -> "linux_$arch.tar.gz"
		}
		val dir = File(work, "navidrome-$VERSION-$asset")
		val exe = File(dir, if (os.contains("win")) "navidrome.exe" else "navidrome")
		if (exe.exists()) return exe
		dir.deleteRecursively()
		dir.mkdirs()
		val archive = File(work, "navidrome_${VERSION}_$asset")
		URI("https://github.com/navidrome/navidrome/releases/download/v$VERSION/navidrome_${VERSION}_$asset")
			.toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
		val sha = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
			.joinToString("") { "%02x".format(it) }
		check(sha == SHA256.getValue(asset)) { "Checksum mismatch for $archive" }
		if (asset.endsWith(".zip")) {
			// not tar: on Windows runners Git's GNU tar can come first on the PATH, and it can't read zips
			ZipInputStream(archive.inputStream()).use { zip ->
				generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
					val out = File(dir, entry.name).canonicalFile
					check(out.path.startsWith(dir.canonicalPath)) { "Bad zip entry ${entry.name}" }
					out.parentFile.mkdirs()
					out.outputStream().use { zip.copyTo(it) }
				}
			}
		} else {
			val tar = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", dir.absolutePath)
				.inheritIO().start()
			check(tar.waitFor() == 0) { "Could not extract $archive" }
		}
		archive.delete()
		exe.setExecutable(true)
		return exe
	}

	private fun waitFor(what: String, seconds: Long = 60, condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
		while (!runCatching(condition).getOrDefault(false)) {
			check(process?.isAlive != false) { "Navidrome exited; see its navidrome.log" }
			check(System.nanoTime() < deadline) { "Timed out waiting for $what" }
			Thread.sleep(200)
		}
	}

	private fun get(path: String): String = request("GET", path, null)

	private fun post(path: String, json: String): String = request("POST", path, json)

	private fun request(method: String, path: String, json: String?): String {
		val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
		conn.requestMethod = method
		conn.connectTimeout = 2000
		conn.readTimeout = 10000
		if (json != null) {
			conn.doOutput = true
			conn.setRequestProperty("Content-Type", "application/json")
			conn.outputStream.use { it.write(json.toByteArray()) }
		}
		check(conn.responseCode in 200..299) { "$method $path: HTTP ${conn.responseCode}" }
		return conn.inputStream.use { it.readBytes().decodeToString() }
	}
}

/** The shared [NavidromeFixture] of this build; test tasks pass it to [usesNavidrome]. */
private fun Project.navidromeFixture(): Provider<NavidromeFixture> = fixture("navidromeFixture", uiTestPort()) {
	musicDir.set(rootProject.layout.projectDirectory.dir("loakUiTest/fixture/music"))
	workDir.set(rootProject.layout.buildDirectory.dir("navidrome-fixture"))
}

/** The large-library instance, on the next port; test tasks pass it to [usesLargeNavidrome]. */
private fun Project.largeNavidromeFixture(): Provider<NavidromeFixture> =
	fixture("largeNavidromeFixture", uiTestPort() + 1) {
		val work = rootProject.layout.buildDirectory.dir("navidrome-fixture/large")
		musicDir.set(work.map { it.dir("music") })
		workDir.set(work)
		generateFrom.set(
			rootProject.layout.projectDirectory.file("loakUiTest/fixture/music/Aurora Test/First Light/02 Low Tide.mp3")
		)
	}

private fun Project.fixture(name: String, port: Int, configure: NavidromeFixture.Params.() -> Unit) =
	gradle.sharedServices.registerIfAbsent(name, NavidromeFixture::class.java) {
		parameters.port.set(port)
		parameters.binDir.set(rootProject.layout.buildDirectory.dir("navidrome-fixture"))
		parameters.configure()
		// one test task at a time: flows change the server's state (a star, the saved
		// queue) that another platform's flows, running alongside, would see
		maxParallelUsages.set(1)
	}

/** The fixture server's port; launchers hand it to the tests (see TestServer in :loakUiTest). */
fun Project.uiTestPort(): Int =
	providers.gradleProperty("loak.uitest.port").map { it.toInt() }.getOrElse(NavidromeFixture.DEFAULT_PORT)

/**
 * Starts the fixture server before [task] runs (if this build hasn't yet) and tells JVM
 * tests its port; Android and browser tests get it from their modules' build files.
 */
fun Project.usesNavidrome(task: org.gradle.api.Task) {
	uses(task, navidromeFixture())
	if (task is org.gradle.api.tasks.testing.Test) task.systemProperty("loak.uitest.port", uiTestPort())
}

/** Starts the large-library server before [task] runs; tests find it on the standard port + 1. */
fun Project.usesLargeNavidrome(task: org.gradle.api.Task) = uses(task, largeNavidromeFixture())

private fun uses(task: org.gradle.api.Task, fixture: Provider<NavidromeFixture>) {
	task.usesService(fixture)
	task.doFirst { fixture.get().start() }
}

/**
 * Lets the app on every device adb sees reach the fixtures from when [task] starts until the
 * build ends: forwards both fixture ports (`adb reverse`), so the flows log in to 127.0.0.1,
 * and gives emulators a network the app counts as online ([JOIN_WIFI]), which cold-booted
 * ones started from a sandboxed shell lack (a managed device, restored from its snapshot, keeps
 * dead Wi-Fi there: see AGENTS.md). Repeated every 2 s: a managed device only appears once the
 * task boots it.
 */
fun Project.connectsDevicesToNavidrome(task: Task, adb: Provider<RegularFile>) {
	val network = gradle.sharedServices.registerIfAbsent("deviceNetwork", DeviceNetwork::class.java) {
		parameters.adb.set(adb)
		parameters.ports.set(listOf(uiTestPort(), uiTestPort() + 1))
	}
	task.usesService(network)
	task.doFirst { network.get().start() }
}

/**
 * Joins the emulator's access point unless it's already connected, with validation off:
 * without internet the network would never validate and the app would stay offline.
 */
private const val JOIN_WIFI = "settings put global captive_portal_mode 0; " +
	"cmd wifi status | grep -q 'Wifi is connected' || cmd wifi connect-network AndroidWifi open"

abstract class DeviceNetwork : BuildService<DeviceNetwork.Params>, AutoCloseable {
	interface Params : BuildServiceParameters {
		val adb: RegularFileProperty
		val ports: ListProperty<Int>
	}

	@Volatile
	private var stopped = false
	private var loop: Thread? = null

	@Synchronized
	fun start() {
		if (loop != null) return
		val joinedWifi = mutableSetOf<String>()
		loop = thread(isDaemon = true, name = "device network") {
			while (!stopped) {
				runCatching {
					for (line in adb("devices").lines().filter { it.endsWith("\tdevice") }) {
						val serial = line.substringBefore('\t')
						for (port in parameters.ports.get()) adb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
						// once per booted emulator
						if (serial.startsWith("emulator-") && serial !in joinedWifi &&
							adb("-s", serial, "shell", "getprop", "sys.boot_completed").trim() == "1"
						) {
							adb("-s", serial, "shell", JOIN_WIFI)
							joinedWifi += serial
						}
					}
				}
				runCatching { Thread.sleep(2000) }
			}
		}
	}

	override fun close() {
		stopped = true
		loop?.interrupt()
	}

	private fun adb(vararg args: String): String {
		val process = ProcessBuilder(parameters.adb.get().asFile.absolutePath, *args)
			.redirectErrorStream(true)
			.start()
		return process.inputStream.bufferedReader().readText().also { process.waitFor() }
	}
}

/**
 * Writes the large library into [dir] once (a marker file keeps it between builds): 200
 * artists × 5 albums × 10 songs, all [source]'s audio under their own ID3v2.3 tags, and two
 * playlists, "Long Mix" (80 songs across artists, a typical queue) and "Big Mix" (5000).
 */
private fun generateLargeLibrary(source: File, dir: File) {
	val marker = File(dir.parentFile, "music.generated")
	if (marker.exists()) return
	dir.deleteRecursively()
	val audio = mpegFrames(source.readBytes())
	val genres = listOf("Ambient", "Electronic", "Jazz", "Rock", "Soul")
	val paths = mutableListOf<String>()
	for (a in 1..200) for (b in 1..5) {
		val artist = "Artist %03d".format(a)
		val album = "Album %03d-%d".format(a, b)
		File(dir, "$artist/$album").mkdirs()
		for (t in 1..10) {
			val title = "Song %03d-%d-%02d".format(a, b, t)
			val path = "$artist/$album/%02d $title.mp3".format(t)
			File(dir, path).outputStream().use {
				it.write(
					id3v23(
						"TIT2" to title, "TPE1" to artist, "TPE2" to artist, "TALB" to album,
						"TRCK" to "$t/10", "TYER" to "${2000 + a % 25}", "TCON" to genres[b - 1],
					)
				)
				it.write(audio)
			}
			paths += path
		}
	}
	// song i of the mix: artist 37·i mod 200 (all distinct), album and track cycling
	val longMix = (0 until 80).map { paths[(it * 37 % 200) * 50 + it % 5 * 10 + it % 10] }
	File(dir, "Long Mix.m3u").writeText((listOf("#EXTM3U") + longMix).joinToString("\n"))
	val bigMix = paths.filterIndexed { i, _ -> i % 2 == 0 }
	File(dir, "Big Mix.m3u").writeText((listOf("#EXTM3U") + bigMix).joinToString("\n"))
	marker.createNewFile()
}

/** [mp3]'s MPEG frames, without its ID3v2 (header, footer) and ID3v1 tags. */
private fun mpegFrames(mp3: ByteArray): ByteArray {
	var start = 0
	if (mp3.decodeToString(0, 3) == "ID3") {
		val size = (6..9).fold(0) { size, i -> size shl 7 or (mp3[i].toInt() and 0x7f) }
		start = 10 + size + if (mp3[5].toInt() and 0x10 != 0) 10 else 0
	}
	val v1 = mp3.decodeToString(mp3.size - 128, mp3.size - 125) == "TAG"
	val end = if (v1) mp3.size - 128 else mp3.size
	return mp3.copyOfRange(start, end)
}

/** A minimal ID3v2.3 tag of Latin-1 text [frames]. */
private fun id3v23(vararg frames: Pair<String, String>): ByteArray {
	val body = ByteArrayOutputStream()
	for ((id, text) in frames) {
		val value = text.toByteArray(Charsets.ISO_8859_1)
		body.write(id.toByteArray())
		body.write(ByteBuffer.allocate(4).putInt(value.size + 1).array())
		body.write(byteArrayOf(0, 0, 0)) // no flags, Latin-1
		body.write(value)
	}
	// "ID3", version 3.0, no flags, then the size in 7-bit bytes
	val size = (3 downTo 0).map { (body.size() shr 7 * it and 0x7f).toByte() }
	return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size + body.toByteArray()
}
