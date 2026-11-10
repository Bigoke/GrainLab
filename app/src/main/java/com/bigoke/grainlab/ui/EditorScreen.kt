package com.bigoke.grainlab.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.grainlab.auto.AutoTools
import com.bigoke.grainlab.data.Settings
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.Adjust
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tool(val label: String) {
    AUTO("Auto"), FILM("Film"), LIGHT("Light"), COLOUR("Colour"), PRESENCE("Presence"), HSL("HSL"),
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
    var splitMode by remember { mutableStateOf(false) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var beforeImg by remember { mutableStateOf<Bitmap?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    var tool by remember { mutableStateOf(Tool.entries[Settings.editorTool.coerceIn(0, Tool.entries.size - 1)]) }
    LaunchedEffect(tool) { Settings.editorTool = tool.ordinal }
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

    fun clampPan(p: Offset, z: Float): Offset {
        val maxX = (z - 1f) * boxSize.width / 2f
        val maxY = (z - 1f) * boxSize.height / 2f
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(uri) {
        val w = withContext(Dispatchers.IO) { runCatching { BitmapLoader.load(context, uri, 8192) }.getOrNull() }
        if (w == null) { loadFailed = true; return@LaunchedEffect }
        working = w
        previewBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 2048) }
        thumbBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 180) }
    }

    LaunchedEffect(previewBase) {
        val pb = previewBase ?: return@LaunchedEffect
        snapshotFlow { params }.collectLatest { p ->
            rendered = runCatching { Pipeline.render(pb, p) }.getOrNull() ?: rendered
        }
    }

    // "Before" for the split view: same crop and border as the edit, but no colour or effects.
    val beforeKey = params.geometryOnly().values.toString() + params["border"] + params["dateStamp"]
    LaunchedEffect(previewBase, splitMode, beforeKey) {
        val pb = previewBase ?: return@LaunchedEffect
        if (!splitMode) return@LaunchedEffect
        val bp = params.geometryOnly().withAll(mapOf("border" to params["border"], "dateStamp" to params["dateStamp"]))
        beforeImg = runCatching { Pipeline.render(pb, bp, useCache = false) }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        // ---- Top bar (hidden in full screen) ----
        if (!fullScreen) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Color.DarkGray)
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Color.DarkGray)
                }
                IconButton(onClick = { splitMode = !splitMode }) {
                    Icon(Icons.Filled.Compare, "Before and after", tint = if (splitMode) Amber else Color.White)
                }
                IconButton(onClick = { showSaveTemplate = true }) {
                    Icon(Icons.Filled.BookmarkAdd, "Save as template", tint = Color.White)
                }
                TextButton(onClick = { showExport = true }, enabled = working != null) {
                    Text("Export", color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- Photo: tap for full screen, hold to see the original, pinch or double-tap to zoom ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { boxSize = it }
                .pointerInput(splitMode) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val z = (zoom * zoomChange).coerceIn(1f, 6f)
                        zoom = z
                        pan = if (z <= 1.01f) Offset.Zero else clampPan(pan + panChange, z)
                    }
                }
                .pointerInput(splitMode) {
                    detectTapGestures(
                        onTap = { fullScreen = !fullScreen },
                        onDoubleTap = {
                            if (zoom > 1.1f) { zoom = 1f; pan = Offset.Zero } else { zoom = 2.5f; pan = Offset.Zero }
                        },
                        onPress = {
                            if (!splitMode) coroutineScope {
                                val job = launch { delay(300); showOriginal = true }
                                tryAwaitRelease()
                                job.cancel()
                                showOriginal = false
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (showOriginal) previewBase else (rendered ?: previewBase)
            val before = beforeImg
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = pan.x; translationY = pan.y
                },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loadFailed -> Text("Could not open this photo.", color = Muted)
                    shown == null -> CircularProgressIndicator(color = Amber)
                    splitMode && before != null -> SplitCompare(before, shown, split) { split = it }
                    else -> Image(
                        shown.asImageBitmap(),
                        contentDescription = "Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (showOriginal) Pill("Original", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            if (zoom > 1.05f) Pill(String.format(Locale.US, "%.1fx", zoom), Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (fullScreen) Pill("Tap to show tools", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
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

        if (!fullScreen) {
            // ---- Tool panel (sized to its content, so the photo keeps most of the screen) ----
            Column(Modifier.fillMaxWidth().background(Panel)) {
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
                    )
                    Tool.FILM -> FilmPanel(
                        thumbBase = thumbBase,
                        selectedId = params.filmId,
                        strength = params["filmStrength"],
                        onSelect = { look -> commit(params.withFilm(look)) },
                        onStrength = { params = params.with("filmStrength", it) },
                        onStrengthDone = { commit(params) },
                    )
                    Tool.LIGHT -> ChipSliderPanel(specsFor(Group.LIGHT), params, { params = it }, { commit(params) })
                    Tool.COLOUR -> ChipSliderPanel(specsFor(Group.COLOUR), params, { params = it }, { commit(params) })
                    Tool.PRESENCE -> ChipSliderPanel(specsFor(Group.PRESENCE), params, { params = it }, { commit(params) })
                    Tool.GRADING -> ChipSliderPanel(specsFor(Group.GRADING), params, { params = it }, { commit(params) })
                    Tool.DETAIL -> ChipSliderPanel(
                        specsFor(Group.DETAIL), params, { params = it }, { commit(params) },
                        extraLabel = "Auto denoise",
                        onExtra = {
                            val w = working ?: return@ChipSliderPanel
                            runBusy("Measuring noise…") {
                                val v = withContext(Dispatchers.Default) { AutoTools.autoNoise(w) }
                                commit(params.with("noiseReduction", v))
                            }
                        },
                    )
                    Tool.EFFECTS -> EffectsPanel(params, { params = it }, { commit(params) }, { commit(it) })
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

            // ---- Tool tabs ----
            Row(
                Modifier.fillMaxWidth().background(Ink).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Tool.entries.forEach { t ->
                    val sel = tool == t
                    Text(
                        t.label,
                        color = if (sel) Amber else Color.White,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { tool = t }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
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

private fun specsFor(group: Group): List<Adjust> = Adjustments.all.filter { it.group == group && it.step == 0f }

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

/** Small pill used for picking one adjustment; shows a dot when it has been changed. */
@Composable
private fun ToolChip(label: String, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Amber else Color(0xFF2A2622))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (changed) {
            Box(Modifier.size(6.dp).background(if (selected) Color(0xFF2B1A00) else Amber, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (selected) Color(0xFF2B1A00) else Color.White, fontSize = 12.sp)
    }
}

/** One slider at a time: pick the adjustment from a row of chips. Double-tap the value to reset. */
@Composable
private fun SingleSlider(spec: Adjust, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = spec.min..spec.max,
            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
            modifier = Modifier.weight(1f),
        )
        Text(
            formatValue(value, spec.min, spec.max),
            color = if (abs(value - spec.default) > 1e-3f) Amber else Muted,
            fontSize = 13.sp,
            modifier = Modifier
                .width(52.dp)
                .padding(start = 8.dp)
                .pointerInput(spec.key) {
                    detectTapGestures(onDoubleTap = { onChange(spec.default); onDone() })
                },
        )
    }
}

@Composable
private fun ChipSliderPanel(
    specs: List<Adjust>,
    params: EditParams,
    onChange: (EditParams) -> Unit,
    onDone: () -> Unit,
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
) {
    var key by remember(specs) { mutableStateOf(specs.first().key) }
    val spec = specs.firstOrNull { it.key == key } ?: specs.first()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            specs.forEach { s ->
                ToolChip(s.label, s.key == spec.key, abs(params[s.key] - s.default) > 1e-3f) { key = s.key }
            }
            if (extraLabel != null) {
                Text(
                    extraLabel,
                    color = Amber, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onExtra).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        SingleSlider(spec, params[spec.key], { onChange(params.with(spec.key, it)) }, onDone)
    }
}

@Composable
private fun EffectsPanel(params: EditParams, onChange: (EditParams) -> Unit, onDone: () -> Unit, onCommit: (EditParams) -> Unit) {
    val specs = remember { specsFor(Group.EFFECTS) }
    var key by remember { mutableStateOf(specs.first().key) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            specs.forEach { s -> ToolChip(s.label, key == s.key, abs(params[s.key] - s.default) > 1e-3f) { key = s.key } }
            ToolChip("Border", key == "border", params["border"] > 0.5f) { key = "border" }
            ToolChip("Date stamp", key == "dateStamp", params["dateStamp"] > 0.5f) { key = "dateStamp" }
        }
        when (key) {
            "border" -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Adjustments.borderNames.forEachIndexed { i, name ->
                    Choice(name, params["border"].roundToInt() == i) { onCommit(params.with("border", i.toFloat())) }
                }
            }
            "dateStamp" -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Orange date stamp in the corner", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = params["dateStamp"] > 0.5f,
                    onCheckedChange = { onCommit(params.with("dateStamp", if (it) 1f else 0f)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Amber),
                )
            }
            else -> {
                val spec = specs.first { it.key == key }
                SingleSlider(spec, params[spec.key], { onChange(params.with(spec.key, it)) }, onDone)
            }
        }
    }
}

@Composable
private fun AutoPanel(
    templates: List<Template>,
    onAutoFix: () -> Unit,
    onAutoAlign: () -> Unit,
    onAutoNoise: () -> Unit,
    onApplyTemplate: (Template) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutoButton("Auto fix", Icons.Filled.AutoFixHigh, onAutoFix)
            AutoButton("Auto align", Icons.Filled.Straighten, onAutoAlign)
            AutoButton("Denoise", Icons.Filled.Grain, onAutoNoise)
            AutoButton("Reset", Icons.Filled.RestartAlt, onReset)
        }
        if (templates.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(templates, key = { it.id }) { t ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2622))
                            .clickable { onApplyTemplate(t) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text((if (t.favourite) "★ " else "") + t.name, color = Color.White, fontSize = 13.sp)
                        Text(FilmLooks.byId(t.params.filmId)?.name ?: "Template", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2622))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
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
    var pack by remember { mutableStateOf<String?>(FilmLooks.byId(selectedId)?.pack) }
    val looks = remember(pack) { FilmLooks.all.filter { pack == null || it.pack == pack } }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolChip("All", pack == null, false) { pack = null }
            FilmLooks.packs.forEach { p -> ToolChip(FilmLooks.packLabel(p), pack == p, FilmLooks.byId(selectedId)?.pack == p) { pack = p } }
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
                Slider(
                    value = strength,
                    onValueChange = onStrength,
                    onValueChangeFinished = onStrengthDone,
                    colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                    modifier = Modifier.weight(1f),
                )
                Text("${(strength * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
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
        Modifier.width(66.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
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
            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HslPanel(params: EditParams, onChange: (EditParams) -> Unit, onDone: () -> Unit) {
    var band by remember { mutableIntStateOf(0) }
    var part by remember { mutableStateOf('s') }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Adjustments.bands.forEachIndexed { i, (name, hue) ->
                val rgb = FloatArray(3).also { LutBuilder.hsvToRgb(hue, 0.85f, 0.95f, it) }
                Box(
                    Modifier
                        .size(if (band == i) 30.dp else 24.dp)
                        .clip(CircleShape)
                        .background(Color(rgb[0], rgb[1], rgb[2]))
                        .border(if (band == i) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { band = i },
                )
            }
            Spacer(Modifier.width(6.dp))
            listOf('h' to "Hue", 's' to "Sat", 'l' to "Lum").forEach { (c, label) ->
                ToolChip(label, part == c, abs(params[Adjustments.hslKey(band, c)]) > 1e-3f) { part = c }
            }
        }
        val key = Adjustments.hslKey(band, part)
        val spec = Adjust(key, Adjustments.bands[band].first, Group.HIDDEN, -1f, 1f)
        SingleSlider(spec, params[key], { onChange(params.with(key, it)) }, onDone)
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
    val specs = remember { listOf("straighten", "perspV", "perspH").mapNotNull { Adjustments.spec(it) } }
    var key by remember { mutableStateOf("straighten") }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            Adjustments.cropNames.forEachIndexed { i, name ->
                ToolChip(name, params["crop"].roundToInt() == i, false) { onCommit(params.with("crop", i.toFloat())) }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            specs.forEach { s -> ToolChip(s.label, key == s.key, abs(params[s.key]) > 1e-3f) { key = s.key } }
            Text(
                "Auto align", color = Amber, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAutoAlign).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        val spec = specs.first { it.key == key }
        SingleSlider(spec, params[key], { onChange(params.with(key, it)) }, onDone)
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
    val formats = ExportFormat.entries
    var format by remember { mutableStateOf(formats[Settings.exportFormat.coerceIn(0, formats.size - 1)]) }
    val sizes = listOf("Full" to 16384, "4096 px" to 4096, "2048 px" to 2048, "1080 px" to 1080)
    var sizeIndex by remember { mutableIntStateOf(Settings.exportSize.coerceIn(0, sizes.size - 1)) }
    var quality by remember { mutableFloatStateOf(Settings.exportQuality.toFloat().coerceIn(60f, 100f)) }
    fun go(share: Boolean) {
        Settings.exportFormat = format.ordinal
        Settings.exportSize = sizeIndex
        Settings.exportQuality = quality.roundToInt()
        onExport(format, sizes[sizeIndex].second, quality.roundToInt(), share)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column {
                Text("Format", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    formats.forEach { f -> Choice(f.label, format == f) { format = f } }
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
                TextButton(onClick = { go(true) }) { Text("Save & share") }
                TextButton(onClick = { go(false) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Before/after view: drag anywhere on the photo to move the divider. */
@Composable
private fun SplitCompare(before: Bitmap, after: Bitmap, split: Float, onSplit: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onSplit((it.x / size.width).coerceIn(0f, 1f)) },
                ) { change, _ ->
                    change.consume()
                    onSplit((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Image(after.asImageBitmap(), "After", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Image(
            before.asImageBitmap(), "Before", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().drawWithContent {
                clipRect(left = 0f, top = 0f, right = size.width * split, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * split
            val y = size.height / 2f
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
            drawCircle(Color.White, radius = 18.dp.toPx(), center = Offset(x, y))
            drawCircle(Amber, radius = 15.dp.toPx(), center = Offset(x, y))
            val a = 6.dp.toPx()
            drawLine(Color.Black, Offset(x - a, y), Offset(x - a * 0.2f, y - a * 0.7f), strokeWidth = 4f)
            drawLine(Color.Black, Offset(x - a, y), Offset(x - a * 0.2f, y + a * 0.7f), strokeWidth = 4f)
            drawLine(Color.Black, Offset(x + a, y), Offset(x + a * 0.2f, y - a * 0.7f), strokeWidth = 4f)
            drawLine(Color.Black, Offset(x + a, y), Offset(x + a * 0.2f, y + a * 0.7f), strokeWidth = 4f)
        }
        Pill("Before", Modifier.align(Alignment.TopStart).padding(8.dp))
        Pill("After", Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
