import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * The machine the build runs on, named the way Nucleus' jars name their native folders
 * (`nucleus/native/<nucleus>/`) and the way Rust names its targets. Desktop packages are
 * built per host, so the host is the only platform a native gets built for.
 */
enum class HostPlatform(val nucleus: String, val rustTarget: String) {
	LinuxX64("linux-x64", "x86_64-unknown-linux-gnu"),
	LinuxArm64("linux-aarch64", "aarch64-unknown-linux-gnu"),
	MacX64("darwin-x64", "x86_64-apple-darwin"),
	MacArm64("darwin-aarch64", "aarch64-apple-darwin"),
	WindowsX64("win32-x64", "x86_64-pc-windows-msvc"),
	WindowsArm64("win32-aarch64", "aarch64-pc-windows-msvc");

	val isLinux get() = this == LinuxX64 || this == LinuxArm64

	/** The file name a Rust `cdylib` called [name] is built as here. */
	fun libraryFile(name: String) = when (this) {
		LinuxX64, LinuxArm64 -> "lib$name.so"
		MacX64, MacArm64 -> "lib$name.dylib"
		WindowsX64, WindowsArm64 -> "$name.dll"
	}

	companion object {
		val current: HostPlatform by lazy {
			val os = System.getProperty("os.name").lowercase()
			val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
			when {
				os.startsWith("mac") -> if (arm) MacArm64 else MacX64
				os.startsWith("windows") -> if (arm) WindowsArm64 else WindowsX64
				else -> if (arm) LinuxArm64 else LinuxX64
			}
		}
	}
}

/** [path] of a Git [repository] at [tag], with [patches] applied (`git apply`, from its root). */
class GitSource(
	@get:Input val repository: String,
	@get:Input val tag: String,
	@get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) val patches: FileCollection,
	/** The only directory checked out, or the whole repository when empty. */
	@get:Input val path: String = "",
	/** Whether the crate's unit tests run before the build: the patches carry tests of their own. */
	@get:Input val tests: Boolean = false,
)

/**
 * Builds the Rust crate in [source] into a native library for this machine, at
 * `<outputDir>/<platform>/<library file>`: [swapNucleusNatives] puts it in Nucleus' jar in
 * place of the one Nucleus ships. A patch that no longer applies fails the build: the upstream
 * moved and the patch needs redoing (or dropping, once the upstream ships the change).
 */
@CacheableTask
abstract class BuildPatchedNative @Inject constructor(
	private val exec: ExecOperations,
	private val fs: FileSystemOperations,
) : DefaultTask() {
	@get:Nested
	abstract val source: Property<GitSource>

	/** The crate's `cdylib` name, e.g. `nucleus_rodio`. */
	@get:Input
	abstract val library: Property<String>

	/**
	 * crates.io dependencies of the crate built from patched sources instead, by crate name.
	 * The version checked out must still be the one the crate's Cargo.lock asks for, or Cargo
	 * would leave the patched source out; the build fails then.
	 */
	@get:Nested
	abstract val patchedCrates: MapProperty<String, GitSource>

	@get:Input
	val platform = HostPlatform.current.name

	@get:OutputDirectory
	abstract val outputDir: DirectoryProperty

	@TaskAction
	fun build() {
		val host = HostPlatform.valueOf(platform)
		val work = temporaryDir
		fs.delete { delete(work) }
		try {
			val crate = checkout(source.get(), File(work, "crate"))
			val overrides = patchedCrates.get().flatMap { (name, dependency) ->
				val dir = checkout(dependency, File(work, name))
				if (dependency.tests) test(dir, work)
				// a TOML literal string: Windows' command line drops the double quotes of a basic one
				listOf("--config", "patch.crates-io.$name.path='${dir.invariantSeparatorsPath}'")
			}
			if (source.get().tests) test(crate, work, overrides)
			run(
				"cargo", "build", "--release",
				"--manifest-path", File(crate, "Cargo.toml").path,
				"--target", host.rustTarget,
				"--target-dir", File(work, "target").path,
				*overrides.toTypedArray(),
			)
			for (name in patchedCrates.get().keys) {
				check(isPathDependency(File(crate, "Cargo.lock"), name)) {
					"Cargo left the patched $name out: ${source.get().repository} no longer " +
						"uses the version patched, redo the patch on the version it does use"
				}
			}
			val file = host.libraryFile(library.get())
			fs.delete { delete(outputDir) }
			fs.copy {
				from(File(work, "target/${host.rustTarget}/release/$file"))
				into(outputDir.dir(host.nucleus))
			}
		} finally {
			fs.delete { delete(work) }
		}
	}

	/** Checks [source] out into [dir] and returns the directory of its [GitSource.path]. */
	private fun checkout(source: GitSource, dir: File): File {
		// autocrlf off: Git for Windows would check out CRLF lines the (LF) patches don't match
		val clone = mutableListOf(
			"git", "-c", "core.autocrlf=false", "-c", "advice.detachedHead=false",
			"clone", "-q", "--depth", "1", "--branch", source.tag, "--filter=blob:none",
		)
		if (source.path.isNotEmpty()) clone += "--sparse"
		run(*clone.toTypedArray(), source.repository, dir.path)
		if (source.path.isNotEmpty()) run("git", "-C", dir.path, "sparse-checkout", "set", source.path)
		val patches = source.patches.files.sorted().map { it.path }
		if (patches.isNotEmpty()) run("git", "-C", dir.path, "apply", *patches.toTypedArray())
		return File(dir, source.path)
	}

	private fun test(crate: File, work: File, overrides: List<String> = emptyList()) {
		run(
			"cargo", "test", "--lib",
			"--manifest-path", File(crate, "Cargo.toml").path,
			"--target-dir", File(work, "target").path,
			*overrides.toTypedArray(),
		)
	}

	private fun run(vararg command: String) {
		exec.exec { commandLine(*command) }
	}

	/** Whether [lockfile] takes [crate] from a path (a `[patch]`) rather than a registry. */
	private fun isPathDependency(lockfile: File, crate: String): Boolean =
		lockfile.readText().split("[[package]]").any { block ->
			block.contains("name = \"$crate\"\n") && !block.contains("source = ")
		}
}

/**
 * Swaps the native libraries in [natives] ([BuildPatchedNative] outputs) into the Nucleus
 * jars on [configurations], in place of the ones Nucleus ships for the same platform.
 */
fun Project.swapNucleusNatives(natives: FileCollection, vararg configurations: String) {
	val swapped = Attribute.of("loak.nucleusNativesSwapped", Boolean::class.javaObjectType)
	dependencies.attributesSchema { attribute(swapped) }
	dependencies.artifactTypes.getByName("jar").attributes.attribute(swapped, false)
	dependencies.registerTransform(SwapNucleusNatives::class.java) {
		from.attribute(swapped, false)
		to.attribute(swapped, true)
		parameters.natives.from(natives)
	}
	for (name in configurations) {
		this.configurations.named(name) { attributes.attribute(swapped, true) }
	}
}

abstract class SwapNucleusNatives : TransformAction<SwapNucleusNatives.Parameters> {
	interface Parameters : TransformParameters {
		/** Directories of `<platform>/<library file>`, the platforms named as in Nucleus' jars. */
		@get:InputFiles
		@get:PathSensitive(PathSensitivity.RELATIVE)
		val natives: ConfigurableFileCollection
	}

	@get:InputArtifact
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	abstract val input: Provider<FileSystemLocation>

	override fun transform(outputs: TransformOutputs) {
		val jar = input.get().asFile
		if (!jar.name.startsWith("nucleus.")) {
			outputs.file(jar)
			return
		}
		// "<platform>/<library file>" -> the library built for it
		val ours = parameters.natives.flatMap { dir ->
			dir.walk().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath to it }
		}.toMap()
		val names = ours.keys.map { it.substringAfter('/') }.toSet()
		ZipFile(jar).use { zip ->
			val entries = zip.entries().toList()
			val mine = entries.filter { it.name.substringAfterLast('/') in names }
			if (mine.isEmpty()) {
				outputs.file(jar)
				return
			}
			var replaced = 0
			ZipOutputStream(outputs.file(jar.name).outputStream()).use { out ->
				for (entry in entries) {
					val replacement = ours[entry.name.removePrefix("nucleus/native/")]
					out.putNextEntry(ZipEntry(entry.name))
					(replacement?.inputStream() ?: zip.getInputStream(entry)).use { it.copyTo(out) }
					if (replacement != null) replaced++
				}
			}
			check(replaced > 0) { "none of ${ours.keys} is a native of ${jar.name}" }
		}
	}
}
