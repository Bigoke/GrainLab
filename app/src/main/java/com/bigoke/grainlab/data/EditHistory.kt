package com.bigoke.grainlab.data

import android.content.Context
import android.net.Uri
import com.bigoke.grainlab.edit.EditParams
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Keeps edits re-editable: the last edit of every opened photo, and for every saved photo the
 * original it came from and the edit that made it.
 */
object EditHistory {
    private fun key(uri: Uri): String {
        val d = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun dir(context: Context, name: String) = File(context.filesDir, name).apply { mkdirs() }

    fun saveEdit(context: Context, source: Uri, params: EditParams) {
        runCatching { File(dir(context, "edits"), key(source) + ".json").writeText(params.toJson().toString()) }
    }

    fun loadEdit(context: Context, source: Uri): EditParams? = runCatching {
        val f = File(dir(context, "edits"), key(source) + ".json")
        if (f.exists()) EditParams.fromJson(JSONObject(f.readText())) else null
    }.getOrNull()

    fun clearEdit(context: Context, source: Uri) {
        File(dir(context, "edits"), key(source) + ".json").delete()
    }

    /** Remembers that [output] was made from [source] (null for camera shots) with [params]. */
    fun recordOutput(context: Context, output: Uri, source: Uri?, params: EditParams) {
        runCatching {
            val o = JSONObject()
            o.put("source", source?.toString() ?: JSONObject.NULL)
            o.put("params", params.toJson())
            File(dir(context, "outputs"), key(output) + ".json").writeText(o.toString())
        }
    }

    /** For a saved photo: the original to re-edit (if known) and the edit used. */
    fun lookupOutput(context: Context, output: Uri): Pair<Uri?, EditParams>? = runCatching {
        val f = File(dir(context, "outputs"), key(output) + ".json")
        if (!f.exists()) return@runCatching null
        val o = JSONObject(f.readText())
        val src = if (o.isNull("source")) null else Uri.parse(o.getString("source"))
        src to EditParams.fromJson(o.getJSONObject("params"))
    }.getOrNull()
}
