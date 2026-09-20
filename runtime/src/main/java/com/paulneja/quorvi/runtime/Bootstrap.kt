package com.paulneja.quorvi.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

data class BootstrapAsset(val abi: String, val url: String, val sha256: String)

/** Termux binaries carry "/data/data/com.termux" inside them; the same-length app id is written over it byte for byte. */
class Prefix(target: String) {
    private val from = FROM.toByteArray()
    private val to = target.toByteArray()
    private val toText = target

    init {
        require(target.length == FROM.length) { "The app id must be exactly ${FROM.length} characters to host the shell, not \"$target\"" }
    }

    fun rewrite(bytes: ByteArray): ByteArray {
        var i = indexOf(bytes, 0) ?: return bytes
        val out = bytes.copyOf()
        while (true) {
            System.arraycopy(to, 0, out, i, to.size)
            i = indexOf(bytes, i + from.size) ?: return out
        }
    }

    fun rewrite(text: String): String = text.replace(FROM, toText)

    private fun indexOf(bytes: ByteArray, start: Int): Int? {
        outer@ for (i in start..bytes.size - from.size) {
            for (j in from.indices) if (bytes[i + j] != from[j]) continue@outer
            return i
        }
        return null
    }

    companion object {
        const val FROM = "com.termux"
    }
}

class Bootstrap(private val context: Context, private val http: OkHttpClient = OkHttpClient()) {
    private val rewriter = Prefix(context.packageName)
    val files: File = File("/data/data/${context.packageName}/files")
    val prefix = File(files, "usr")
    val home = File(files, "home")
    val ready: Boolean get() = File(prefix, "bin/sh").exists() && File(prefix, ".nexo-ready").exists()

    fun asset(): BootstrapAsset? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return ASSETS[abi]
    }

    fun install(progress: (String) -> Unit) {
        if (ready) {
            writeAptHook()
            return
        }
        val asset = asset() ?: error("Unsupported ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        val zip = File(context.cacheDir, "bootstrap-${asset.abi}.zip")
        if (!zip.exists() || sha256(zip) != asset.sha256) {
            progress("download")
            download(asset.url, zip)
        }
        val actual = sha256(zip)
        check(actual == asset.sha256) { "Bootstrap checksum mismatch: $actual" }
        progress("extract")
        val staging = File(files, "usr-staging").apply { deleteRecursively(); mkdirs() }
        extract(zip.inputStream(), staging)
        prefix.deleteRecursively()
        check(staging.renameTo(prefix)) { "Could not move the bootstrap into place" }
        home.mkdirs()
        File(prefix, "tmp").mkdirs()
        progress("configure")
        writeAptHook()
        runSecondStage()
        File(prefix, ".nexo-ready").writeText(asset.sha256)
        zip.delete()
        progress("ready")
    }

    private fun download(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            target.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
    }

    private fun extract(input: InputStream, into: File) {
        val symlinks = mutableListOf<Pair<String, String>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    File(into, entry.name).mkdirs()
                    continue
                }
                val bytes = zip.readBytes()
                if (entry.name == "SYMLINKS.txt") {
                    String(bytes).lineSequence().filter { it.contains('←') }.forEach { line ->
                        val (target, link) = line.split('←', limit = 2)
                        symlinks += rewriter.rewrite(target) to link
                    }
                    continue
                }
                val out = File(into, entry.name)
                require(out.canonicalPath.startsWith(into.canonicalPath)) { "Zip entry escapes the prefix: ${entry.name}" }
                out.parentFile?.mkdirs()
                out.writeBytes(rewriter.rewrite(bytes))
                Os.chmod(out.absolutePath, if (isExecutable(entry.name)) 0b111_000_000 else 0b110_000_000)
            }
        }
        symlinks.forEach { (target, link) ->
            val file = File(into, link.removePrefix("./"))
            file.parentFile?.mkdirs()
            file.delete()
            Os.symlink(target, file.absolutePath)
        }
    }

    private fun isExecutable(name: String): Boolean =
        name.startsWith("bin/") || name.startsWith("libexec/") || name.startsWith("lib/apt/") || name.contains("/bin/") || name.endsWith(".so") || name.contains(".so.")

    private fun writeAptHook() {
        val script = File(prefix, "libexec/nexo/patch-debs")
        script.parentFile?.mkdirs()
        script.writeText(PATCH_DEBS.replace("@PREFIX@", prefix.absolutePath).replace("@APP@", context.packageName))
        Os.chmod(script.absolutePath, 0b111_000_000)

        val chosen = File(prefix, "etc/termux/chosen_mirrors")
        if (!chosen.exists() && File(prefix, "etc/termux/mirrors/default").exists()) {
            runCatching { Os.symlink(File(prefix, "etc/termux/mirrors/default").absolutePath, chosen.absolutePath) }
            File(prefix, "etc/apt/sources.list").writeText("deb https://packages-cf.termux.dev/apt/termux-main stable main\n")
        }
        val conf = File(prefix, "etc/apt/apt.conf.d/99-nexo-rewrite")
        conf.parentFile?.mkdirs()
        conf.writeText("DPkg::Pre-Install-Pkgs {\"${script.absolutePath}\";};\n")
    }

    private fun runSecondStage() {
        val stage = File(prefix, "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh")
        if (!stage.exists()) return
        val process = ProcessBuilder(File(prefix, "bin/sh").absolutePath, stage.absolutePath)
            .directory(home)
            .redirectErrorStream(true)
            .apply { environment().putAll(Environment.of(this@Bootstrap)) }
            .start()
        process.inputStream.bufferedReader().readText()
        process.waitFor()
    }

    companion object {
        private const val RELEASE = "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.10.04-r1%2Bapt.android-7"

        val ASSETS = mapOf(
            "arm64-v8a" to BootstrapAsset("aarch64", "$RELEASE/bootstrap-aarch64.zip", "c671bde0d9923ede9d9e972634a04042c93b1589b9a2ca9bc5c38848234e8c5b"),
            "x86_64" to BootstrapAsset("x86_64", "$RELEASE/bootstrap-x86_64.zip", "9933e09342ec96df3f912483db622f7b7ba78d4e627c80951d0123b6b2fff750"),
        )

        fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        private val PATCH_DEBS = """
            #!@PREFIX@/bin/sh
            set -e
            export PATH="@PREFIX@/bin"
            while read -r deb; do
              case "${'$'}deb" in *.deb) ;; *) continue ;; esac
              [ -f "${'$'}deb" ] || continue
              work=${'$'}(mktemp -d "@PREFIX@/tmp/nexo-deb.XXXXXX")
              dpkg-deb -R "${'$'}deb" "${'$'}work/pkg"
              if [ -d "${'$'}work/pkg/data/data/com.termux" ]; then
                mv "${'$'}work/pkg/data/data/com.termux" "${'$'}work/pkg/data/data/@APP@"
              fi
              grep -rlZ 'com\.termux' "${'$'}work/pkg" 2>/dev/null | xargs -0 -r sed -i 's/com\.termux/@APP@/g'
              for script in preinst postinst prerm postrm config; do
                if [ -f "${'$'}work/pkg/DEBIAN/${'$'}script" ]; then chmod 0755 "${'$'}work/pkg/DEBIAN/${'$'}script"; fi
              done
              find "${'$'}work/pkg" -type l | while read -r link; do
                target=${'$'}(readlink "${'$'}link")
                case "${'$'}target" in *com.termux*) ln -sfn "${'$'}(printf '%s' "${'$'}target" | sed 's/com\.termux/@APP@/g')" "${'$'}link" ;; esac
              done
              dpkg-deb -b "${'$'}work/pkg" "${'$'}deb" >/dev/null
              rm -rf "${'$'}work"
            done
        """.trimIndent() + "\n"
    }
}

object Environment {
    fun of(bootstrap: Bootstrap): Map<String, String> {
        val prefix = bootstrap.prefix.absolutePath
        val preload = listOf("lib/libtermux-exec-ld-preload.so", "lib/libtermux-exec.so").map { File(bootstrap.prefix, it) }.firstOrNull { it.exists() }
        return buildMap {
            put("PREFIX", prefix)
            put("HOME", bootstrap.home.absolutePath)
            put("TMPDIR", "$prefix/tmp")
            put("PATH", "$prefix/bin")
            put("LANG", "en_US.UTF-8")
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("SHELL", "$prefix/bin/bash")
            put("TERMUX_PREFIX", prefix)
            put("ANDROID_DATA", "/data")
            put("ANDROID_ROOT", "/system")
            System.getenv("BOOTCLASSPATH")?.let { put("BOOTCLASSPATH", it) }
            System.getenv("ANDROID_ART_ROOT")?.let { put("ANDROID_ART_ROOT", it) }
            System.getenv("ANDROID_I18N_ROOT")?.let { put("ANDROID_I18N_ROOT", it) }
            System.getenv("ANDROID_TZDATA_ROOT")?.let { put("ANDROID_TZDATA_ROOT", it) }
            preload?.let { put("LD_PRELOAD", it.absolutePath) }
        }
    }
}
