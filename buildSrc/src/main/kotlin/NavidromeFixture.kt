import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * The real Subsonic server the UI flows run against: a pinned Navidrome on
 * 127.0.0.1 (port [DEFAULT_PORT], or -Ploak.uitest.port) serving the committed library in `loakUiTest/fixture/music`,
 * with one admin user. Started on first use by any test task that needs it
 * (Android emulators reach it as 10.0.2.2), stopped when the build ends.
 * Every build starts from an empty Navidrome database.
 */
abstract class NavidromeFixture : BuildService<NavidromeFixture.Params>, AutoCloseable {
	interface Params : BuildServiceParameters {
		val musicDir: DirectoryProperty
		val workDir: DirectoryProperty
		val port: Property<Int>
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
		val data = File(work, "data").apply { deleteRecursively(); mkdirs() }
		check(runCatching { get("/ping") }.isFailure) {
			"Port $port is already in use: is another build running UI tests? Pick another with -Ploak.uitest.port"
		}
		process = ProcessBuilder(binary(work).absolutePath)
			.redirectErrorStream(true)
			.redirectOutput(File(work, "navidrome.log"))
			.apply {
				environment().putAll(
					mapOf(
						"ND_MUSICFOLDER" to parameters.musicDir.get().asFile.absolutePath,
						"ND_DATAFOLDER" to data.absolutePath,
						"ND_PORT" to "$port",
						"ND_LOGLEVEL" to "warn",
						// hermetic: no telemetry, no Last.fm/Spotify/Deezer lookups
						"ND_ENABLEINSIGHTSCOLLECTOR" to "false",
						"ND_ENABLEEXTERNALSERVICES" to "false",
						// the Android emulator reaches the host's loopback as 10.0.2.2
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
		check(get("/rest/getPlaylists?$auth").contains("UI Test Mix")) { "fixture playlist not imported" }
	}

	override fun close() {
		process?.apply {
			destroy()
			if (!waitFor(10, TimeUnit.SECONDS)) destroyForcibly()
		}
	}

	private fun waitForScan() {
		Thread.sleep(500) // let a just-requested scan start before polling it
		waitFor("the library scan") { get("/rest/getScanStatus?$auth").contains("\"scanning\":false") }
	}

	private fun binary(work: File): File {
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

	private fun waitFor(what: String, condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
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
fun Project.navidromeFixture(): Provider<NavidromeFixture> =
	gradle.sharedServices.registerIfAbsent("navidromeFixture", NavidromeFixture::class.java) {
		parameters.musicDir.set(rootProject.layout.projectDirectory.dir("loakUiTest/fixture/music"))
		parameters.workDir.set(rootProject.layout.buildDirectory.dir("navidrome-fixture"))
		parameters.port.set(uiTestPort())
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
	val fixture = navidromeFixture()
	task.usesService(fixture)
	task.doFirst { fixture.get().start() }
	if (task is org.gradle.api.tasks.testing.Test) task.systemProperty("loak.uitest.port", uiTestPort())
}
