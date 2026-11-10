package com.bigoke.grainlab.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.grainlab.auto.AutoTools
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.Adjustments
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.FilmLook
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Geometry
import com.bigoke.grainlab.edit.Group
import com.bigoke.grainlab.edit.LutBuilder
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.io.BitmapLoader
import com.bigoke.grainlab.io.ExportFormat
import com.bigoke.grainlab.io.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tool(val label: String) {
    AUTO("Auto"), FILM("Film"), LIGHT("Light"), COLOUR("Colour"), HSL("HSL"),
    GRADING("Grading"), DETAIL("Detail"), EFFECTS("Effects"), CROP("Crop")
}

@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates by TemplateStore.items.collectAsState()

    var working by remember { mutableStateOf<Bitmap?>(null) }
    var previewBase by remember { mutableStateOf<Bitmap?>(null) }
    var thumbBase by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    var params by remember { mutableStateOf(EditParams()) }
    var committed by remember { mutableStateOf(EditParams()) }
    val undoStack = remember { mutableStateListOf<EditParams>() }
    val redoStack = remember { mutableStateListOf<EditParams>() }

    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.FILM) }
    var busy by remember { mutableStateOf<String?>(null) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    fun commit(p: EditParams) {
        if (p != committed) {
            undoStack.add(committed)
            if (undoStack.size > 60) undoStack.removeAt(0)
            redoStack.clear()
            committed = p
        }
        params = p
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(committed)
        committed = undoStack.removeAt(undoStack.lastIndex)
        params = committed
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(committed)
        committed = redoStack.removeAt(redoStack.lastIndex)
        params = committed
    }

    fun runBusy(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        scope.launch {
            busy = label
            try { block() } catch (e: Exception) { context.toast("Something went wrong: ${e.message}") }
            busy = null
        }
    }

    LaunchedEffect(uri) {
        val w = withContext(Dispatchers.IO) { runCatching { BitmapLoader.load(context, uri, 4096) }.getOrNull() }
        if (w == null) { loadFailed = true; return@LaunchedEffect }
        working = w
        previewBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 1440) }
        thumbBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 180) }
    }

    LaunchedEffect(previewBase) {
        val pb = previewBase ?: return@LaunchedEffect
        snapshotFlow { params }.collectLatest { p ->
            rendered = runCatching { Pipeline.render(pb, p) }.getOrNull() ?: rendered
        }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        // ---- Top bar ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Edit", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Color.DarkGray)
            }
            IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Color.DarkGray)
            }
            IconButton(onClick = { showSaveTemplate = true }) {
                Icon(Icons.Filled.BookmarkAdd, "Save as template", tint = Color.White)
            }
            Button(onClick = { showExport = true }, enabled = working != null, modifier = Modifier.padding(end = 8.dp)) {
                Text("Export")
            }
        }

        // ---- Image ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (showOriginal) previewBase else (rendered ?: previewBase)
            when {
                loadFailed -> Text("Could not open this photo.", color = Muted)
                shown == null -> CircularProgressIndicator(color = Amber)
                else -> Image(
                    shown.asImageBitmap(),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            if (showOriginal) Pill("Original", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            else if (shown != null) Text(
                "Hold to compare",
                color = Color(0x88FFFFFF), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
            busy?.let { label ->
                Column(
                    Modifier.background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(label, color = Color.White)
                }
            }
        }

        // ---- Tool tabs ----
        ScrollableTabRow(
            selectedTabIndex = tool.ordinal,
            containerColor = Panel,
            contentColor = Amber,
            edgePadding = 8.dp,
        ) {
            Tool.entries.forEach { t ->
                Tab(
                    selected = tool == t,
                    onClick = { tool = t },
                    text = { Text(t.label, fontSize = 13.sp, color = if (tool == t) Amber else Color.White) },
                )
            }
        }

        // ---- Tool panel ----
        Box(Modifier.fillMaxWidth().height(250.dp).background(Panel)) {
            when (tool) {
                Tool.AUTO -> AutoPanel(
                    templates = templates,
                    onAutoFix = {
                        val pb = previewBase ?: return@AutoPanel
                        runBusy("Auto fixing…") {
                            val m = withContext(Dispatchers.Default) { AutoTools.autoFix(pb) }
                            commit(params.withAll(m))
                        }
                    },
                    onAutoAlign = {
                        val pb = previewBase ?: return@AutoPanel
                        runBusy("Aligning…") {
                            val m = withContext(Dispatchers.Default) { autoAlign(pb, params) }
                            commit(params.withAll(m))
                            val deg = m["straighten"] ?: 0f
                            context.toast(
                                if (abs(deg) < 0.05f && abs(m["perspV"] ?: 0f) < 0.01f) "Already straight"
                                else String.format(Locale.US, "Straightened %.1f°", deg),
                            )
                        }
                    },
                    onAutoNoise = {
                        val w = working ?: return@AutoPanel
                        runBusy("Measuring noise…") {
                            val v = withContext(Dispatchers.Default) { AutoTools.autoNoise(w) }
                            commit(params.with("noiseReduction", v))
                            context.toast(
                                when {
                                    v < 0.05f -> "Very little noise found"
                                    v < 0.35f -> "Light noise reduction applied"
                                    else -> "Strong noise reduction applied"
                                },
                            )
                        }
                    },
                    onApplyTemplate = { t -> commit(t.applyTo(params)) },
                    onReset = { commit(EditParams()) },
                    onSaveTemplate = { showSaveTemplate = true },
                )
                Tool.FILM -> FilmPanel(
                    thumbBase = thumbBase,
                    selectedId = params.filmId,
                    strength = params["filmStrength"],
                    onSelect = { look -> commit(params.withFilm(look)) },
                    onStrength = { params = params.with("filmStrength", it) },
                    onStrengthDone = { commit(params) },
                )
                Tool.LIGHT -> SliderPanel(Group.LIGHT, params, { params = it }, { commit(params) })
                Tool.COLOUR -> SliderPanel(Group.COLOUR, params, { params = it }, { commit(params) })
                Tool.GRADING -> SliderPanel(Group.GRADING, params, { params = it }, { commit(params) })
                Tool.DETAIL -> SliderPanel(Group.DETAIL, params, { params = it }, { commit(params) }) {
                    OutlinedButton(
                        onClick = {
                            val w = working ?: return@OutlinedButton
                            runBusy("Measuring noise…") {
                                val v = withContext(Dispatchers.Default) { AutoTools.autoNoise(w) }
                                commit(params.with("noiseReduction", v))
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Auto noise reduction") }
                }
                Tool.EFFECTS -> SliderPanel(Group.EFFECTS, params, { params = it }, { commit(params) }) {
                    Text("Border", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Adjustments.borderNames.forEachIndexed { i, name ->
                            Choice(name, params["border"].roundToInt() == i) { commit(params.with("border", i.toFloat())) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Date stamp", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = params["dateStamp"] > 0.5f,
                            onCheckedChange = { commit(params.with("dateStamp", if (it) 1f else 0f)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Amber),
                        )
                    }
                }
                Tool.HSL -> HslPanel(params, { params = it }, { commit(params) })
                Tool.CROP -> CropPanel(
                    params = params,
                    onChange = { params = it },
                    onDone = { commit(params) },
                    onCommit = { commit(it) },
                    onAutoAlign = {
                        val pb = previewBase ?: return@CropPanel
                        runBusy("Aligning…") {
                            val m = withContext(Dispatchers.Default) { autoAlign(pb, params) }
                            commit(params.withAll(m))
                        }
                    },
                )
            }
        }
    }

    if (showSaveTemplate) {
        SaveTemplateDialog(
            onDismiss = { showSaveTemplate = false },
            onSave = { name, includeCrop ->
                TemplateStore.create(name, params, includeCrop)
                showSaveTemplate = false
                context.toast("Template \"$name\" saved. Use it here or in the camera.")
            },
        )
    }

    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onExport = { format, maxSide, quality, share ->
                showExport = false
                val w = working ?: return@ExportDialog
                runBusy("Saving…") {
                    val p = params
                    val uriOut = withContext(Dispatchers.Default) {
                        val src = BitmapLoader.scaleToMax(w, maxSide)
                        val out = Pipeline.render(src, p)
                        MediaSaver.saveBitmap(context, out, format, quality, "GrainLab_${MediaSaver.timestamp()}")
                    }
                    if (uriOut == null) context.toast("Could not save")
                    else {
                        context.toast("Saved to Pictures/GrainLab")
                        if (share) shareImage(context, uriOut, format.mime)
                    }
                }
            },
        )
    }
}

/** Auto align works on the photo after rotation/flip but before perspective and straightening. */
private fun autoAlign(preview: Bitmap, p: EditParams): Map<String, Float> {
    val rot = ((p["rotation"].roundToInt() % 4) + 4) % 4
    val flip = p["flipH"] > 0.5f
    val base = if (rot != 0 || flip) Geometry.rotateFlip(preview, rot, flip) else preview
    val m = AutoTools.autoAlign(base)
    if (base !== preview) base.recycle()
    return m
}

private fun shareImage(context: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share photo"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoPanel(
    templates: List<Template>,
    onAutoFix: () -> Unit,
    onAutoAlign: () -> Unit,
    onAutoNoise: () -> Unit,
    onApplyTemplate: (Template) -> Unit,
    onReset: () -> Unit,
    onSaveTemplate: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoButton("Auto fix", Icons.Filled.AutoFixHigh, onAutoFix)
            AutoButton("Auto align", Icons.Filled.Straighten, onAutoAlign)
            AutoButton("Auto denoise", Icons.Filled.Grain, onAutoNoise)
            AutoButton("Reset all", Icons.Filled.RestartAlt, onReset)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My templates", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSaveTemplate) { Text("Save current as template") }
        }
        if (templates.isEmpty()) {
            Text("No templates yet. Make an edit you like and save it as a template.", color = Muted, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates, key = { it.id }) { t ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2622))
                            .clickable { onApplyTemplate(t) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text((if (t.favourite) "★ " else "") + t.name, color = Color.White, fontSize = 13.sp)
                        Text(FilmLooks.byId(t.params.filmId)?.name ?: "Custom", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White)
    }
}

@Composable
private fun FilmPanel(
    thumbBase: Bitmap?,
    selectedId: String?,
    strength: Float,
    onSelect: (FilmLook?) -> Unit,
    onStrength: (Float) -> Unit,
    onStrengthDone: () -> Unit,
) {
    var pack by remember { mutableStateOf<String?>(null) }
    val looks = remember(pack) { FilmLooks.all.filter { pack == null || it.pack == pack } }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Choice("All", pack == null) { pack = null }
            FilmLooks.packs.forEach { p -> Choice(p, pack == p) { pack = p } }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            item(key = "none") { FilmThumb(null, thumbBase, selectedId == null) { onSelect(null) } }
            items(looks, key = { it.id }) { look ->
                FilmThumb(look, thumbBase, selectedId == look.id) { onSelect(look) }
            }
        }
        if (selectedId != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", color = Muted, fontSize = 12.sp)
                Slider(
                    value = strength,
                    onValueChange = onStrength,
                    onValueChangeFinished = onStrengthDone,
                    colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(strength * 100).roundToInt()}", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FilmThumb(look: FilmLook?, base: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    var img by remember(look?.id, base) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(look?.id, base) {
        val b = base ?: return@LaunchedEffect
        img = runCatching {
            Pipeline.render(b, EditParams().withFilm(look).with("border", 0f).with("dateStamp", 0f), finishing = false, useCache = false)
        }.getOrNull()
    }
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(if (selected) 2.dp else 0.dp, if (selected) Amber else Color.Transparent, RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2622)),
        ) {
            val shown = img ?: base
            if (shown != null) Image(
                shown.asImageBitmap(), contentDescription = look?.name ?: "Original",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            look?.name ?: "Original",
            color = if (selected) Amber else Color.White,
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SliderPanel(
    group: Group,
    params: EditParams,
    onChange: (EditParams) -> Unit,
    onDone: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val specs = remember(group) { Adjustments.all.filter { it.group == group && it.step == 0f } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        specs.forEach { s ->
            AdjustSlider(s.label, params[s.key], s.min, s.max, s.default, { onChange(params.with(s.key, it)) }, onDone)
        }
        extra()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HslPanel(params: EditParams, onChange: (EditParams) -> Unit, onDone: () -> Unit) {
    var band by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Adjustments.bands.forEachIndexed { i, (name, hue) ->
                val rgb = FloatArray(3).also { LutBuilder.hsvToRgb(hue, 0.85f, 0.95f, it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { band = i }) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(rgb[0], rgb[1], rgb[2]))
                            .border(if (band == i) 3.dp else 0.dp, Color.White, CircleShape),
                    )
                    Text(name, color = if (band == i) Amber else Muted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        listOf('h' to "Hue", 's' to "Saturation", 'l' to "Luminance").forEach { (part, label) ->
            val key = Adjustments.hslKey(band, part)
            AdjustSlider(label, params[key], -1f, 1f, 0f, { onChange(params.with(key, it)) }, onDone)
        }
    }
}

@Composable
private fun CropPanel(
    params: EditParams,
    onChange: (EditParams) -> Unit,
    onDone: () -> Unit,
    onCommit: (EditParams) -> Unit,
    onAutoAlign: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { onCommit(params.with("rotation", ((params["rotation"].roundToInt() + 3) % 4).toFloat())) }) {
                Icon(Icons.Filled.RotateLeft, "Rotate left", tint = Color.White)
            }
            IconButton(onClick = { onCommit(params.with("rotation", ((params["rotation"].roundToInt() + 1) % 4).toFloat())) }) {
                Icon(Icons.Filled.RotateRight, "Rotate right", tint = Color.White)
            }
            IconButton(onClick = { onCommit(params.with("flipH", if (params["flipH"] > 0.5f) 0f else 1f)) }) {
                Icon(Icons.Filled.Flip, "Flip", tint = Color.White)
            }
            TextButton(onClick = onAutoAlign) { Text("Auto align") }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Adjustments.cropNames.forEachIndexed { i, name ->
                Choice(name, params["crop"].roundToInt() == i) { onCommit(params.with("crop", i.toFloat())) }
            }
        }
        listOf("straighten", "perspV", "perspH").forEach { key ->
            val s = Adjustments.spec(key)!!
            AdjustSlider(s.label, params[key], s.min, s.max, s.default, { onChange(params.with(key, it)) }, onDone)
        }
    }
}

@Composable
private fun SaveTemplateDialog(onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var includeCrop by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as template") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { includeCrop = !includeCrop }) {
                    Checkbox(checked = includeCrop, onCheckedChange = { includeCrop = it })
                    Text("Include crop, rotation and straighten")
                }
                Text("Film, light, colour, detail and effects are always included.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim().ifEmpty { "My template" }, includeCrop) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, Int, Int, Boolean) -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var sizeIndex by remember { mutableIntStateOf(0) }
    var quality by remember { mutableFloatStateOf(92f) }
    val sizes = listOf("Full" to 4096, "2048 px" to 2048, "1080 px" to 1080)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column {
                Text("Format", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportFormat.entries.forEach { f -> Choice(f.label, format == f) { format = f } }
                }
                Text("Size (longest side)", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizes.forEachIndexed { i, (label, _) -> Choice(label, sizeIndex == i) { sizeIndex = i } }
                }
                if (format != ExportFormat.PNG) {
                    Text("Quality ${quality.roundToInt()}", color = Muted, fontSize = 12.sp)
                    Slider(
                        value = quality, onValueChange = { quality = it }, valueRange = 60f..100f,
                        colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                    )
                }
                Text("Location and camera data are not copied to the exported photo.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onExport(format, sizes[sizeIndex].second, quality.roundToInt(), true) }) { Text("Save & share") }
                TextButton(onClick = { onExport(format, sizes[sizeIndex].second, quality.roundToInt(), false) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
