package com.reel.editor

import com.reel.editor.fx.FxDef
import com.reel.editor.fx.TransitionDef
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shader catalog (assets/fx) that FxCatalog loads at startup: ids and names are unique,
 * params are well formed, and no shader uses GLSL ES 3 features that phones running the
 * GLSL ES 1.00 path reject. Compiling and rendering is covered by tools/fx/validate_shaders.py,
 * tools/fx/render_check.py and FxThumbsDeviceTest.
 */
class FxCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fx = listOf(File("src/main/assets/fx"), File("app/src/main/assets/fx")).first { it.isDirectory }
    private val lib = json.decodeFromString<List<FxDef>>(File(fx, "library.json").readText())
    private val transitions = json.decodeFromString<List<TransitionDef>>(File(fx, "transitions.json").readText())

    private val componentCount = mapOf("float" to 1, "int" to 1, "bool" to 1, "vec2" to 2, "ivec2" to 2, "vec3" to 3, "color" to 3, "vec4" to 4)

    // Desktop / ES 3 only: built-ins, unsigned ints, integer modulo, shifts, switch.
    private val es3Only = Regex(
        """\b(texture|round|roundEven|trunc|tanh|sinh|cosh|asinh|acosh|atanh|inverse|transpose|determinant|textureSize|texelFetch|isnan|isinf|outerProduct)\s*\(|\buint\b|%|<<|>>|\bswitch\b""",
    )

    private fun stripComments(code: String) =
        code.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("""//[^\n]*"""), "")

    @Test
    fun catalogIsLarge() {
        val kinds = lib.groupingBy { it.kind }.eachCount()
        assertTrue("filters ${kinds["filter"]}", (kinds["filter"] ?: 0) >= 140)
        assertTrue("effects ${kinds["effect"]}", (kinds["effect"] ?: 0) >= 150)
        assertTrue("anims ${kinds["anim"]}", (kinds["anim"] ?: 0) >= 50)
        assertTrue("transitions ${transitions.size}", transitions.size >= 300)
        assertEquals(setOf("filter", "effect", "anim"), kinds.keys)
    }

    @Test
    fun idsAndNamesAreUnique() {
        val ids = lib.map { it.id } + transitions.map { it.id }
        assertEquals(ids.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), ids.size, ids.toSet().size)
        for ((kind, items) in lib.groupBy { it.kind }) {
            val names = items.map { it.name.lowercase() }
            val dupes = names.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue("duplicate $kind names: $dupes", dupes.isEmpty())
        }
        val tNames = transitions.map { it.name.lowercase() }
        assertTrue("duplicate transition names", tNames.size == tNames.toSet().size)
    }

    @Test
    fun everyShaderHasItsEntryPoint() {
        for (e in lib) assertTrue(e.id, Regex("""vec4\s+effect\s*\(\s*vec2""").containsMatchIn(e.code))
        for (t in transitions) assertTrue(t.id, Regex("""vec4\s+transition\s*\(\s*vec2""").containsMatchIn(t.code))
        for (e in lib + transitions.map { FxDef(it.id, it.name, "transition", it.category, it.params, it.code) }) {
            assertTrue("${e.id} has no category", e.category.isNotBlank())
            assertTrue("${e.id} has no name", e.name.isNotBlank())
        }
    }

    @Test
    fun paramsAreWellFormed() {
        val reserved = setOf("uTex", "uResolution", "uTime", "uDuration", "uProgress", "uIntensity", "uSeed", "PI", "effect", "luma", "rand", "noise")
        for (e in lib) for (p in e.params) {
            val n = componentCount[p.type]
            assertTrue("${e.id}.${p.name}: unknown type ${p.type}", n != null)
            assertEquals("${e.id}.${p.name}: default size", n, p.default.size)
            assertTrue("${e.id}.${p.name}: reserved name", p.name !in reserved)
            assertTrue("${e.id}.${p.name}: min > max", p.min <= p.max)
            if (p.type == "float") assertTrue("${e.id}.${p.name}: default out of range", p.default[0] in p.min..p.max)
        }
    }

    @Test
    fun shadersStayInsideGlslEs100() {
        val offenders = (lib.map { it.id to it.code } + transitions.map { it.id to it.code })
            .mapNotNull { (id, code) -> es3Only.find(stripComments(code))?.let { "$id: ${it.value}" } }
        assertTrue("GLSL ES 3 only constructs: $offenders", offenders.isEmpty())
    }
}
