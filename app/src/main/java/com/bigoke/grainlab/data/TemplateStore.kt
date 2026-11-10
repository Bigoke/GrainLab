package com.bigoke.grainlab.data

import android.content.Context
import com.bigoke.grainlab.edit.EditParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A saved edit ("recipe") that can be applied to photos or used live in the camera. */
data class Template(
    val id: String,
    val name: String,
    val params: EditParams,
    val includesGeometry: Boolean = false,
    val favourite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
) {
    /** The params to apply on top of an existing edit (keeps that edit's crop unless included). */
    fun applyTo(current: EditParams): EditParams {
        val base = if (includesGeometry) params else params.withoutGeometry()
        return if (includesGeometry) base else base.withAll(current.geometryOnly().values)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("format", "grainlab-template")
        put("version", 1)
        put("id", id)
        put("name", name)
        put("includesGeometry", includesGeometry)
        put("favourite", favourite)
        put("created", created)
        put("params", params.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): Template = Template(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name", "Template"),
            params = EditParams.fromJson(o.optJSONObject("params") ?: JSONObject()),
            includesGeometry = o.optBoolean("includesGeometry", false),
            favourite = o.optBoolean("favourite", false),
            created = o.optLong("created", System.currentTimeMillis()),
        )
    }
}

object TemplateStore {
    private val _items = MutableStateFlow<List<Template>>(emptyList())
    val items: StateFlow<List<Template>> = _items.asStateFlow()
    private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "templates").apply { mkdirs() }
        reload()
    }

    private fun reload() {
        val d = dir ?: return
        val list = d.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { Template.fromJson(JSONObject(f.readText())) }.getOrNull()
        } ?: emptyList()
        _items.value = list.sortedWith(compareByDescending<Template> { it.favourite }.thenByDescending { it.created })
    }

    fun save(t: Template) {
        val d = dir ?: return
        File(d, "${t.id}.json").writeText(t.toJson().toString(2))
        reload()
    }

    fun create(name: String, params: EditParams, includesGeometry: Boolean): Template {
        val stored = if (includesGeometry) params else params.withoutGeometry()
        val t = Template(UUID.randomUUID().toString(), name.ifBlank { "My template" }, stored, includesGeometry)
        save(t)
        return t
    }

    fun delete(t: Template) {
        val d = dir ?: return
        File(d, "${t.id}.json").delete()
        reload()
    }

    /** Imports a shared template file. Always gets a new id so it never overwrites. */
    fun import(text: String): Template {
        val t = Template.fromJson(JSONObject(text)).copy(
            id = UUID.randomUUID().toString(),
            created = System.currentTimeMillis(),
        )
        save(t)
        return t
    }
}
