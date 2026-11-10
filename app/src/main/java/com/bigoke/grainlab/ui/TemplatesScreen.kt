package com.bigoke.grainlab.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.Adjustments
import com.bigoke.grainlab.edit.FilmLooks
import java.io.File
import kotlin.math.abs

@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val templates by TemplateStore.items.collectAsState()
    var renaming by remember { mutableStateOf<Template?>(null) }
    var deleting by remember { mutableStateOf<Template?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val t = text?.let { runCatching { TemplateStore.import(it) }.getOrNull() }
        context.toast(if (t != null) "Imported \"${t.name}\"" else "That file is not a GrainLab template")
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("My templates", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Icon(Icons.Filled.FileOpen, null, tint = Amber)
                Text(" Import")
            }
        }
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No templates yet.\n\nEdit a photo, then tap the bookmark icon to save your edit as a template. " +
                        "Templates appear in the editor and in the camera's look strip.",
                    color = Muted,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates, key = { it.id }) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(14.dp))
                            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(summary(t), color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { TemplateStore.save(t.copy(favourite = !t.favourite)) }) {
                            Icon(if (t.favourite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite", tint = Amber)
                        }
                        IconButton(onClick = { renaming = t }) { Icon(Icons.Filled.Edit, "Rename", tint = Color.White) }
                        IconButton(onClick = { shareTemplate(context, t) }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                        IconButton(onClick = { deleting = t }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                    }
                }
            }
        }
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename template") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    TemplateStore.save(t.copy(name = name.trim().ifEmpty { t.name }))
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${t.name}\"?") },
            text = { Text("This removes the template from this phone. Photos you already edited are not changed.") },
            confirmButton = { TextButton(onClick = { TemplateStore.delete(t); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun summary(t: Template): String {
    val film = FilmLooks.byId(t.params.filmId)?.name ?: "No film"
    val changed = t.params.values.count { (k, v) -> abs(v - Adjustments.default(k)) > 1e-3f }
    val crop = if (t.includesGeometry) " · includes crop" else ""
    return "$film · $changed adjustments$crop"
}

private fun shareTemplate(context: Context, t: Template) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val safe = t.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40).ifEmpty { "template" }
    val file = File(dir, "$safe.grainlab.json")
    file.writeText(t.toJson().toString(2))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "GrainLab template: ${t.name}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share template"))
}
