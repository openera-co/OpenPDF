package com.reel.editor.fx

import android.content.Context
import com.reel.editor.nativebridge.NativeCore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ParamDef(
    val name: String,
    val type: String,
    val default: List<Float> = emptyList(),
    val min: Float = 0f,
    val max: Float = 1f,
)

@Serializable
data class FxDef(
    val id: String,
    val name: String,
    val kind: String,
    val category: String,
    val params: List<ParamDef> = emptyList(),
    val code: String,
)

@Serializable
data class TransitionDef(
    val id: String,
    val name: String,
    val category: String,
    val author: String = "",
    val license: String = "MIT",
    val params: List<ParamDef> = emptyList(),
    val code: String,
)

data class LookDef(val index: Int, val id: String, val name: String, val category: String)

/** Everything the app can apply, loaded once from assets and the native look generator. */
class FxCatalog private constructor(
    val filters: List<FxDef>,
    val effects: List<FxDef>,
    val anims: List<FxDef>,
    val transitions: List<TransitionDef>,
    val looks: List<LookDef>,
    private val prelude: String,
    private val preludeMin: String,
    private val wrapSingle: String,
    private val wrapTransition: String,
    val vertex: String,
    private val internal: Map<String, String>,
) {
    private val byId: Map<String, FxDef> = (filters + effects + anims).associateBy { it.id }
    private val transitionById = transitions.associateBy { it.id }
    private val lookById = looks.associateBy { it.id }

    fun fx(id: String): FxDef? = byId[id]
    fun transition(id: String): TransitionDef? = transitionById[id]
    fun look(id: String): LookDef? = lookById[id]

    fun singleSource(def: FxDef): String {
        val params = def.params.joinToString("\n") { "uniform ${glslType(it.type)} ${it.name};" }
        return "#version 100\n" + prelude + wrapSingle.replace("//@PARAMS", params).replace("//@CODE", def.code)
    }

    fun transitionSource(def: TransitionDef): String =
        "#version 100\n" + preludeMin + wrapTransition.replace("//@CODE", def.code)

    fun internalSource(name: String): String =
        "#version 100\n" + prelude + "#line 1\n" + (internal[name] ?: error("missing internal shader $name"))

    val totalCount: Int get() = filters.size + effects.size + anims.size + transitions.size + looks.size

    companion object {
        @Volatile private var instance: FxCatalog? = null
        private val json = Json { ignoreUnknownKeys = true }

        fun glslType(t: String) = if (t == "color") "vec3" else t

        fun get(context: Context): FxCatalog = instance ?: synchronized(this) {
            instance ?: load(context.applicationContext).also { instance = it }
        }

        private fun load(context: Context): FxCatalog {
            val a = context.assets
            fun text(path: String) = a.open(path).bufferedReader().use { it.readText() }
            val lib = json.decodeFromString<List<FxDef>>(text("fx/library.json"))
            val trans = json.decodeFromString<List<TransitionDef>>(text("fx/transitions.json"))
            val looks = (0 until NativeCore.lookCount()).mapNotNull { i ->
                NativeCore.lookInfo(i)?.let { LookDef(i, it[0], it[1], it[2]) }
            }
            val internal = a.list("fx/internal").orEmpty().associate { it.removeSuffix(".frag") to text("fx/internal/$it") }
            return FxCatalog(
                filters = lib.filter { it.kind == "filter" },
                effects = lib.filter { it.kind == "effect" },
                anims = lib.filter { it.kind == "anim" },
                transitions = trans,
                looks = looks,
                prelude = text("fx/prelude.glsl"),
                preludeMin = text("fx/prelude_min.glsl"),
                wrapSingle = text("fx/wrap_single.glsl"),
                wrapTransition = text("fx/wrap_transition.glsl"),
                vertex = text("fx/vertex.vert"),
                internal = internal,
            )
        }
    }
}
