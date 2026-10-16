package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphParser
import io.github.dreadboy.navigationshortcuts.anycons.source.GlyphSource
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Test access to Niagara's glyphs and textures, decoded from the git-ignored `vendor/niagara.apk` with
 * `apktool d -s` into `anycons/build/niagara-decoded/` (once; reused afterwards). Nothing from the APK is committed.
 *
 * When the APK or apktool is missing (e.g. CI), [requireDecoded] skips the calling test via JUnit Assume.
 * Overrides: env `NIAGARA_APK`, env `APKTOOL`.
 */
object NiagaraFixtures {
    private val buildDir: File =
        File(System.getProperty("anycons.buildDir") ?: File(System.getProperty("user.dir"), "build").path)
    private val rootDir: File = File(System.getProperty("anycons.rootDir") ?: System.getProperty("user.dir"))

    val decodedDir: File = File(buildDir, "niagara-decoded")
    private val marker get() = File(decodedDir, "res/drawable/ic_ip_cat_home.xml")

    private var skipReason: String? = null
    private var attempted = false

    fun findApk(): File? {
        System.getenv("NIAGARA_APK")?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        // Walk up from the project root: git worktrees live inside the main checkout, which holds vendor/.
        var dir: File? = rootDir.absoluteFile
        while (dir != null) {
            File(dir, "vendor/niagara.apk").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    fun findApktool(): File? {
        System.getenv("APKTOOL")?.let { File(it) }?.takeIf { it.canExecute() }?.let { return it }
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        return pathDirs.map { File(it, "apktool") }.firstOrNull { it.isFile && it.canExecute() }
    }

    /** Returns the decoded resources dir, decoding on first use; skips the test when that is impossible. */
    @Synchronized
    fun requireDecoded(): File {
        if (!attempted) {
            attempted = true
            skipReason = decode()
        }
        assumeTrue("Niagara fixtures unavailable: $skipReason", skipReason == null)
        return decodedDir
    }

    private fun decode(): String? {
        if (marker.isFile) return null
        val apk = findApk() ?: return "vendor/niagara.apk not found (set NIAGARA_APK)"
        val apktool = findApktool() ?: return "apktool not found on PATH (set APKTOOL)"
        val tmp = File(buildDir, "niagara-decoded.tmp")
        tmp.deleteRecursively()
        buildDir.mkdirs()
        return try {
            val process = ProcessBuilder(apktool.path, "d", "-s", "-f", "-o", tmp.path, apk.path)
                .redirectErrorStream(true)
                .redirectOutput(File(buildDir, "niagara-decode.log"))
                .start()
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return "apktool timed out"
            }
            if (process.exitValue() != 0) return "apktool failed (see build/niagara-decode.log)"
            // Keep only what the tests read, to save disk space.
            File(tmp, "res").listFiles()?.forEach { dir ->
                if (dir.name != "drawable" && dir.name != "drawable-nodpi") dir.deleteRecursively()
            }
            tmp.listFiles()?.forEach { if (it.name != "res") it.deleteRecursively() }
            decodedDir.deleteRecursively()
            if (!tmp.renameTo(decodedDir)) return "could not move decoded resources into place"
            if (marker.isFile) null else "decoded APK lacks ic_ip_cat_home"
        } catch (e: Exception) {
            "apktool could not run: ${e.message}"
        }
    }

    fun glyphFile(name: String): File = File(requireDecoded(), "res/drawable/ic_ip_$name.xml")

    fun parseGlyph(name: String, parser: GlyphParser = GlyphParser()): Glyph =
        glyphFile(name).reader().use { reader -> parser.parse(name, xmlParser(reader.readText())) }

    fun textureFile(texture: Texture): File? =
        File(requireDecoded(), "res/drawable-nodpi").listFiles()
            ?.filter { it.name.startsWith(texture.prefix) }
            ?.minByOrNull { it.name }

    fun decodeTexture(texture: Texture): Bitmap? {
        val file = textureFile(texture) ?: return null
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** A [GlyphSource] over the decoded APK (text XML + texture files). */
    val source: GlyphSource by lazy {
        object : GlyphSource {
            private val glyphs = HashMap<String, Glyph?>()
            private val textures = HashMap<Texture, Bitmap?>()

            override fun glyph(name: String): Glyph? = glyphs.getOrPut(name) {
                val file = glyphFile(name) // may throw AssumptionViolatedException: let the test skip
                try {
                    if (file.isFile) parseGlyph(name) else null
                } catch (e: AssumptionViolatedException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            override fun texture(texture: Texture): Bitmap? = textures.getOrPut(texture) { decodeTexture(texture) }
        }
    }

    fun xmlParser(text: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(text))
    }
}
