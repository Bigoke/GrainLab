package com.bigoke.grainlab.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.io.BitmapLoader
import com.bigoke.grainlab.io.ExportFormat
import com.bigoke.grainlab.io.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apply one template or film look to many photos at once. */
@Composable
fun BatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates by TemplateStore.items.collectAsState()
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var chosen by remember { mutableStateOf<Look>(Look.None) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) { photos = uris; done = 0 }
    }
    val looks = remember(templates) {
        buildList<Look> {
            templates.forEach { add(Look.Tmpl(it)) }
            FilmLooks.all.forEach { add(Look.Film(it)) }
        }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !running) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Batch edit", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !running,
            ) { Text(if (photos.isEmpty()) "1. Choose photos" else "1. ${photos.size} photos chosen (change)") }
            Spacer(Modifier.height(12.dp))
            Text("2. Choose a template or film look", color = Color.White)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(looks, key = { it.key }) { l ->
                val sel = l.key == chosen.key
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (sel) Amber else Panel, RoundedCornerShape(12.dp))
                        .clickable(enabled = !running) { chosen = l }
                        .padding(12.dp),
                ) {
                    Text(l.title, color = if (sel) Color(0xFF2B1A00) else Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (l) {
                            is Look.Tmpl -> "Your template"
                            is Look.Film -> l.film.pack + " · " + l.film.note
                            Look.None -> ""
                        },
                        color = if (sel) Color(0xFF5A3D00) else Muted, fontSize = 12.sp,
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (running || done > 0) {
                LinearProgressIndicator(
                    progress = { if (photos.isEmpty()) 0f else done.toFloat() / photos.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = Amber,
                )
                Text("$done of ${photos.size} saved to Pictures/GrainLab", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    val list = photos
                    val p: EditParams = chosen.params()
                    running = true
                    done = 0
                    scope.launch {
                        for (u in list) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val src = BitmapLoader.load(context, u, 4096) ?: return@runCatching
                                    val out = Pipeline.render(src, p)
                                    MediaSaver.saveBitmap(context, out, ExportFormat.JPEG, 92, "GrainLab_${MediaSaver.timestamp()}")
                                    out.recycle()
                                    src.recycle()
                                }
                            }
                            done++
                        }
                        running = false
                        context.toast("Batch finished")
                    }
                },
                enabled = !running && photos.isNotEmpty() && chosen != Look.None,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "Working…" else "3. Apply to ${photos.size} photos") }
        }
    }
}
