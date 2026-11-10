package com.bigoke.grainlab.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateCodes
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.Adjustments
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Pipeline
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val templates by TemplateStore.items.collectAsState()
    var renaming by remember { mutableStateOf<Template?>(null) }
    var deleting by remember { mutableStateOf<Template?>(null) }
    var showQr by remember { mutableStateOf<Template?>(null) }

    fun importText(text: String?) {
        val json = text?.let { TemplateCodes.decode(it) }
        val t = json?.let { runCatching { TemplateStore.import(it) }.getOrNull() }
        context.toast(if (t != null) "Imported \"${t.name}\"" else "That is not a GrainLab template")
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        importText(text)
    }

    fun scan() {
        runCatching {
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { code -> importText(code.rawValue) }
                .addOnFailureListener { context.toast("Scanner not available: ${it.message}") }
        }.onFailure { context.toast("Scanner not available on this phone") }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("My templates", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { scan() }) { Icon(Icons.Filled.QrCodeScanner, "Scan a template QR code", tint = Amber) }
            IconButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Icon(Icons.Filled.FileOpen, "Import a template file", tint = Amber)
            }
        }
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No templates yet.\n\nEdit a photo, then tap the bookmark icon to save your edit as a template. " +
                        "Templates appear in the editor and in the camera's looks.\n\nYou can also scan a friend's template QR code.",
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
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TemplateThumb(t)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(summary(t), color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { TemplateStore.save(t.copy(favourite = !t.favourite)) }) {
                            Icon(if (t.favourite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite", tint = Amber)
                        }
                        IconButton(onClick = { showQr = t }) { Icon(Icons.Filled.QrCode2, "QR code", tint = Color.White) }
                        IconButton(onClick = { renaming = t }) { Icon(Icons.Filled.Edit, "Rename", tint = Color.White) }
                        IconButton(onClick = { deleting = t }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                    }
                }
            }
        }
    }

    showQr?.let { t ->
        val code = remember(t.id) { TemplateCodes.encode(t) }
        val qr = remember(code) { runCatching { TemplateCodes.qr(code) }.getOrNull() }
        AlertDialog(
            onDismissRequest = { showQr = null },
            title = { Text(t.name) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qr != null) {
                        Image(qr.asImageBitmap(), "Template QR code", modifier = Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text("Scan this in GrainLab (My templates → scan) to copy the look.", color = Muted, fontSize = 12.sp)
                    } else {
                        Text("This template is too large for a QR code. Share it as a file instead.", color = Muted)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { shareTemplateFile(context, t); showQr = null }) {
                        Icon(Icons.Filled.Share, null); Text(" File")
                    }
                    if (qr != null) TextButton(onClick = { shareQr(context, t, qr); showQr = null }) { Text("Share QR") }
                }
            },
            dismissButton = { TextButton(onClick = { showQr = null }) { Text("Close") } },
        )
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

/** The template applied to a small sample scene, so you can see the look at a glance. */
@Composable
private fun TemplateThumb(t: Template) {
    var img by remember(t.id, t.params) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(t.id, t.params) {
        img = runCatching {
            withContext(Dispatchers.Default) { Pipeline.render(TemplateCodes.sampleScene, t.params, useCache = false) }
        }.getOrNull()
    }
    Box(Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2622))) {
        img?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun summary(t: Template): String {
    val film = FilmLooks.byId(t.params.filmId)?.name ?: "No film"
    val changed = t.params.values.count { (k, v) -> abs(v - Adjustments.default(k)) > 1e-3f }
    val crop = if (t.includesGeometry) " · includes crop" else ""
    return "$film · $changed adjustments$crop"
}

private fun shareFile(context: Context, file: File, mime: String, subject: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share template"))
}

private fun safeName(t: Template) = t.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40).ifEmpty { "template" }

private fun shareTemplateFile(context: Context, t: Template) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "${safeName(t)}.grainlab.json")
    file.writeText(t.toJson().toString(2))
    shareFile(context, file, "application/json", "GrainLab template: ${t.name}")
}

private fun shareQr(context: Context, t: Template, qr: Bitmap) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "${safeName(t)}-qr.png")
    file.outputStream().use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(context, file, "image/png", "GrainLab template: ${t.name}")
}
