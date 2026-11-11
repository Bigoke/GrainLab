package com.bigoke.grainlab.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.opengl.GLSurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bigoke.grainlab.auto.AutoTools
import com.bigoke.grainlab.data.EditHistory
import com.bigoke.grainlab.data.Settings
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.Adjust
import com.bigoke.grainlab.edit.Adjustments
import com.bigoke.grainlab.edit.Curve
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.FilmLook
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Geometry
import com.bigoke.grainlab.edit.Group
import com.bigoke.grainlab.edit.LutBuilder
import com.bigoke.grainlab.edit.Mask
import com.bigoke.grainlab.edit.MaskRaster
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.edit.Spot
import com.bigoke.grainlab.edit.Stroke
import com.bigoke.grainlab.gl.EditorGlRenderer
import com.bigoke.grainlab.gl.Effects
import com.bigoke.grainlab.io.BitmapLoader
import com.bigoke.grainlab.io.ExifWriter
import com.bigoke.grainlab.io.ExportFormat
import com.bigoke.grainlab.io.ExportSize
import com.bigoke.grainlab.io.MediaSaver
import com.bigoke.grainlab.io.SocialExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool(val label: String) {
    AUTO("Auto"), FILM("Film"), LIGHT("Light"), COLOUR("Colour"), CURVE("Curve"), PRESENCE("Presence"),
    HSL("HSL"), GRADING("Grading"), MASKS("Masks"), HEAL("Heal"), DETAIL("Detail"), EFFECTS("Effects"), CROP("Crop")
}

/** Copy / paste of edits between photos (kept while the app is open). */
private object EditClipboard {
    var params: EditParams? = null
}

/** Holds the last lookup table so it can be re-sent to the GPU after the view is recreated. */
private class LutHolder {
    var signature: String? = null
    var lut: FloatArray? = null
}

@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val templates by TemplateStore.items.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var sourceUri by remember { mutableStateOf(uri) }
    var working by remember { mutableStateOf<Bitmap?>(null) }
    var previewBase by remember { mutableStateOf<Bitmap?>(null) }
    var thumbBase by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<EditParams?>(null) }
    var reeditOffer by remember { mutableStateOf<Pair<Uri?, EditParams>?>(null) }
    var historyChecked by remember { mutableStateOf(false) }

    var params by remember { mutableStateOf(EditParams()) }
    var committed by remember { mutableStateOf(EditParams()) }
    val undoStack = remember { mutableStateListOf<EditParams>() }
    val redoStack = remember { mutableStateListOf<EditParams>() }

    var geoBase by remember { mutableStateOf<Bitmap?>(null) }
    var smallGeo by remember { mutableStateOf<Bitmap?>(null) }
    var maskBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var maskOverlay by remember { mutableStateOf<Bitmap?>(null) }
    var finished by remember { mutableStateOf<Bitmap?>(null) }
    var hist by remember { mutableStateOf(IntArray(64)) }
    var glGen by remember { mutableIntStateOf(0) }
    var lutVersion by remember { mutableIntStateOf(0) }
    val lutHolder = remember { LutHolder() }

    var tool by remember { mutableStateOf(Tool.entries[Settings.editorTool.coerceIn(0, Tool.entries.size - 1)]) }
    LaunchedEffect(tool) { Settings.editorTool = tool.ordinal }
    var busy by remember { mutableStateOf<String?>(null) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    var splitMode by remember { mutableStateOf(false) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var holdOriginal by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var clip by remember { mutableStateOf(Settings.clipWarning) }
    LaunchedEffect(clip) { Settings.clipWarning = clip }

    var selectedMaskId by remember { mutableStateOf<Long?>(null) }
    var healSize by remember { mutableFloatStateOf(0.03f) }
    var brushSize by remember { mutableFloatStateOf(0.05f) }
    var brushErase by remember { mutableStateOf(false) }
    var liveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var cropRect by remember { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }

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

    // ---- GPU preview ----
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember {
        EditorGlRenderer(glView).also {
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) glView.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) glView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }
    // When the GPU context is new (first start, after rotating or resuming), send everything again.
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            if (renderer.needsImage() && geoBase != null) glGen++
        }
    }
    LaunchedEffect(glGen) {
        geoBase?.let { renderer.setImage(it) }
        lutHolder.lut?.let { renderer.setLut(it) }
        renderer.setMasks(maskBitmaps)
    }

    // ---- Load the photo, and restore earlier edits ----
    LaunchedEffect(sourceUri) {
        runCatching { context.contentResolver.takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val w = withContext(Dispatchers.IO) { runCatching { BitmapLoader.load(context, sourceUri, 8192) }.getOrNull() }
        if (w == null) { loadFailed = true; return@LaunchedEffect }
        loadFailed = false
        working = w
        previewBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 2048) }
        thumbBase = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(w, 180) }
        val restore = pendingRestore
        if (restore != null) {
            pendingRestore = null
            params = restore
            committed = restore
            undoStack.clear()
            redoStack.clear()
        } else if (!historyChecked) {
            historyChecked = true
            val made = EditHistory.lookupOutput(context, sourceUri)
            if (made?.first != null && made.first != sourceUri) {
                reeditOffer = made
            } else {
                EditHistory.loadEdit(context, sourceUri)?.let {
                    if (!it.isIdentity()) {
                        params = it
                        committed = it
                        context.toast("Restored your last edit of this photo")
                    }
                }
            }
        }
    }
    LaunchedEffect(committed, sourceUri) {
        delay(600)
        if (previewBase != null) EditHistory.saveEdit(context, sourceUri, committed)
    }

    // ---- Geometry (rotation, straighten, crop, healing) on the preview copy ----
    val cropMode = tool == Tool.CROP
    val geoKey = (if (cropMode) "crop|" else "final|") + params.geometryOnly().toString()
    LaunchedEffect(previewBase, geoKey) {
        val pb = previewBase ?: return@LaunchedEffect
        val p = params
        val g = withContext(Dispatchers.Default) { if (cropMode) Geometry.uncropped(pb, p) else Geometry.apply(pb, p) }
        geoBase = g
        smallGeo = withContext(Dispatchers.Default) { BitmapLoader.scaleToMax(g, 256) }
        renderer.setImage(g)
    }
    LaunchedEffect(cropMode) {
        if (cropMode) {
            zoom = 1f
            pan = Offset.Zero
            cropRect = floatArrayOf(params["cropL"], params["cropT"], params["cropR"], params["cropB"])
        }
    }

    // ---- Colour lookup table (rebuilt only when colour settings change) ----
    LaunchedEffect(Unit) {
        snapshotFlow { params }.collectLatest { p ->
            val sig = p.lutSignature()
            if (sig != lutHolder.signature) {
                val lut = withContext(Dispatchers.Default) { LutBuilder.build(p) }
                lutHolder.signature = sig
                lutHolder.lut = lut
                renderer.setLut(lut)
                lutVersion++
            }
        }
    }
    LaunchedEffect(smallGeo, lutVersion) {
        val sg = smallGeo ?: return@LaunchedEffect
        val lut = lutHolder.lut ?: return@LaunchedEffect
        hist = withContext(Dispatchers.Default) { lumaHistogram(sg, lut) }
    }

    // ---- Shader values: instant ----
    SideEffect {
        renderer.effects = Effects.from(params)
        renderer.maskParams = Effects.maskParams(params)
        renderer.clip = clip
        renderer.zoom = zoom
        renderer.panX = pan.x
        renderer.panY = pan.y
        renderer.split = when {
            holdOriginal -> 2f
            splitMode -> split
            else -> -1f
        }
    }

    // ---- Masks (shape changes only; strengths are shader values) ----
    val maskShapes = params.masks.map { it.copy(exposure = 0f, contrast = 0f, saturation = 0f, temperature = 0f) }
    LaunchedEffect(geoBase, maskShapes) {
        val g = geoBase ?: return@LaunchedEffect
        val p = params
        val list = withContext(Dispatchers.Default) { MaskRaster.render(p.masks, g, Pipeline.photoKey(g, EditParams())) }
        maskBitmaps = list
        renderer.setMasks(list)
    }
    LaunchedEffect(maskBitmaps, selectedMaskId, tool) {
        val idx = params.masks.indexOfFirst { it.id == selectedMaskId }
        maskOverlay = if (tool == Tool.MASKS && idx in maskBitmaps.indices) {
            withContext(Dispatchers.Default) { tintMask(maskBitmaps[idx]) }
        } else null
    }

    // ---- Borders and the date stamp show on a full render once you stop adjusting ----
    val needsFinish = params["border"] > 0.5f || params["dateStamp"] > 0.5f
    LaunchedEffect(params, needsFinish) {
        finished = null
        val pb = previewBase ?: return@LaunchedEffect
        if (!needsFinish) return@LaunchedEffect
        delay(400)
        finished = runCatching { Pipeline.render(pb, params) }.getOrNull()
    }

    // ---- Where the photo sits on screen (for taps, crop handles, masks) ----
    fun dispRect(): FloatArray? {
        val g = geoBase ?: return null
        if (boxSize.width == 0) return null
        val f = renderer.fitRect(boxSize.width.toFloat(), boxSize.height.toFloat(), g.width.toFloat(), g.height.toFloat())
        val w = f[2] * zoom
        val h = f[3] * zoom
        val cx = boxSize.width / 2f + pan.x
        val cy = boxSize.height / 2f + pan.y
        return floatArrayOf(cx - w / 2f, cy - h / 2f, w, h)
    }

    fun toImage(p: Offset): Offset? {
        val r = dispRect() ?: return null
        return Offset((p.x - r[0]) / r[2], (p.y - r[1]) / r[3])
    }

    fun clampPan(p: Offset, z: Float): Offset {
        val maxX = (z - 1f) * boxSize.width / 2f
        val maxY = (z - 1f) * boxSize.height / 2f
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    val selectedMask = params.masks.firstOrNull { it.id == selectedMaskId }

    fun updateMask(m: Mask, done: Boolean) {
        val next = params.copy(masks = params.masks.map { if (it.id == m.id) m else it })
        if (done) commit(next) else params = next
    }

    // =============================== Photo area ===============================
    val imageArea: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .onSizeChanged { boxSize = it }
                .pointerInput(tool, splitMode, selectedMaskId, selectedMask?.type, brushErase, brushSize, healSize) {
                    var tapJob: Job? = null
                    var lastTap = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = System.currentTimeMillis()
                        var multi = false
                        var moved = false
                        var holdJob: Job? = null
                        val mode = when {
                            tool == Tool.CROP -> "crop"
                            splitMode -> "split"
                            tool == Tool.MASKS && selectedMask?.type == Mask.BRUSH -> "brush"
                            tool == Tool.MASKS && (selectedMask?.type == Mask.RADIAL || selectedMask?.type == Mask.LINEAR) -> "place"
                            tool == Tool.HEAL -> "heal"
                            else -> "view"
                        }
                        if (mode == "crop") return@awaitEachGesture
                        if (mode == "view") holdJob = scope.launch { delay(350); holdOriginal = true }
                        val stroke = ArrayList<Offset>()
                        if (mode == "brush") { stroke.add(down.position); liveStroke = stroke.toList() }
                        if (mode == "split") toImage(down.position)?.let { split = it.x.coerceIn(0f, 1f) }
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                multi = true
                                holdJob?.cancel()
                                holdOriginal = false
                                val z = (zoom * event.calculateZoom()).coerceIn(1f, 6f)
                                zoom = z
                                pan = if (z <= 1.01f) Offset.Zero else clampPan(pan + event.calculatePan(), z)
                                event.changes.forEach { it.consume() }
                            } else if (!multi) {
                                val ch = event.changes.first()
                                val d = ch.positionChange()
                                if (!moved && abs(ch.position.x - down.position.x) + abs(ch.position.y - down.position.y) > viewConfiguration.touchSlop) {
                                    moved = true
                                    holdJob?.cancel()
                                }
                                if (moved) {
                                    when (mode) {
                                        "split" -> toImage(ch.position)?.let { split = it.x.coerceIn(0f, 1f) }
                                        "brush" -> { stroke.add(ch.position); liveStroke = stroke.toList() }
                                        "place" -> params.masks.firstOrNull { it.id == selectedMaskId }?.let { m ->
                                            toImage(ch.position)?.let { u -> updateMask(m.copy(cx = u.x.coerceIn(0f, 1f), cy = u.y.coerceIn(0f, 1f)), false) }
                                        }
                                        else -> if (zoom > 1.01f) pan = clampPan(pan + d, zoom)
                                    }
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        holdJob?.cancel()
                        val wasHold = holdOriginal
                        holdOriginal = false
                        if (multi) return@awaitEachGesture
                        when (mode) {
                            "brush" -> {
                                val m = params.masks.firstOrNull { it.id == selectedMaskId }
                                val pts = stroke.mapNotNull { toImage(it) }.flatMap { listOf(it.x, it.y) }
                                liveStroke = emptyList()
                                if (m != null && pts.isNotEmpty()) {
                                    updateMask(m.copy(strokes = m.strokes + Stroke(pts, brushSize, brushErase)), true)
                                }
                            }
                            "place" -> params.masks.firstOrNull { it.id == selectedMaskId }?.let { m ->
                                if (!moved) {
                                    toImage(down.position)?.let { u ->
                                        updateMask(m.copy(cx = u.x.coerceIn(0f, 1f), cy = u.y.coerceIn(0f, 1f)), true)
                                    }
                                } else commit(params)
                            }
                            "heal" -> if (!moved) {
                                toImage(down.position)?.let { u ->
                                    if (u.x in 0f..1f && u.y in 0f..1f) commit(params.copy(spots = params.spots + Spot(u.x, u.y, healSize)))
                                }
                            }
                            "view" -> if (!moved && !wasHold && System.currentTimeMillis() - start < 350) {
                                val now = System.currentTimeMillis()
                                if (now - lastTap < 300) {
                                    tapJob?.cancel()
                                    if (zoom > 1.1f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f
                                    lastTap = 0L
                                } else {
                                    lastTap = now
                                    tapJob = scope.launch { delay(300); fullScreen = !fullScreen }
                                }
                            }
                        }
                    }
                },
        ) {
            AndroidView(
                factory = {
                    (glView.parent as? ViewGroup)?.removeView(glView)
                    glView
                },
                modifier = Modifier.fillMaxSize(),
            )
            val r = dispRect()
            // Full render with border / date stamp once you stop adjusting.
            val fin = finished
            if (fin != null && !splitMode && !holdOriginal && zoom <= 1.01f && tool !in setOf(Tool.CROP, Tool.HEAL, Tool.MASKS)) {
                Image(fin.asImageBitmap(), "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().background(Color.Black))
            }
            // The selected mask, shown in red.
            val ov = maskOverlay
            if (ov != null && r != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        ov.asImageBitmap(),
                        dstOffset = IntOffset(r[0].roundToInt(), r[1].roundToInt()),
                        dstSize = IntSize(r[2].roundToInt(), r[3].roundToInt()),
                    )
                }
            }
            if (r != null) {
                Canvas(Modifier.fillMaxSize()) {
                    if (liveStroke.size > 1) {
                        val path = Path().apply {
                            moveTo(liveStroke[0].x, liveStroke[0].y)
                            liveStroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path, if (brushErase) Color(0x88FFFFFF) else Color(0x88FF4040),
                            style = DrawStroke(width = brushSize * min(r[2], r[3]) * 2f),
                        )
                    }
                    val sm = selectedMask
                    if (tool == Tool.MASKS && sm != null && (sm.type == Mask.RADIAL || sm.type == Mask.LINEAR)) {
                        val c = Offset(r[0] + sm.cx * r[2], r[1] + sm.cy * r[3])
                        drawCircle(Color.White, 14f, c, style = DrawStroke(4f))
                        drawCircle(Amber, 8f, c)
                        if (sm.type == Mask.LINEAR) {
                            val a = Math.toRadians(sm.angle.toDouble())
                            val dx = (kotlin.math.cos(a) * r[2]).toFloat()
                            val dy = (kotlin.math.sin(a) * r[2]).toFloat()
                            drawLine(Color.White, c - Offset(dx, dy), c + Offset(dx, dy), strokeWidth = 2f)
                        }
                    }
                    if (tool == Tool.HEAL) {
                        val side = min(r[2], r[3])
                        params.spots.forEach { s ->
                            drawCircle(Color.White, s.r * side, Offset(r[0] + s.x * r[2], r[1] + s.y * r[3]), style = DrawStroke(2f))
                        }
                    }
                    if (splitMode) {
                        val x = r[0] + split * r[2]
                        drawLine(Color.White, Offset(x, r[1]), Offset(x, r[1] + r[3]), strokeWidth = 3f)
                        drawCircle(Amber, 15.dp.toPx(), Offset(x, r[1] + r[3] / 2f))
                    }
                }
                if (cropMode) {
                    CropOverlay(r, cropRect, params, geoBase) { rect, done ->
                        cropRect = rect
                        if (done) commit(params.withAll(mapOf("cropL" to rect[0], "cropT" to rect[1], "cropR" to rect[2], "cropB" to rect[3])))
                    }
                }
            }
            when {
                loadFailed -> Text("Could not open this photo.", color = Muted, modifier = Modifier.align(Alignment.Center))
                geoBase == null -> CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.Center))
            }
            if (splitMode) {
                Pill("Before", Modifier.align(Alignment.TopStart).padding(8.dp))
                Pill("After", Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (holdOriginal) Pill("Original", Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            if (zoom > 1.05f) Pill(String.format(Locale.US, "%.1fx", zoom), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (fullScreen) Pill("Tap to show tools", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            val hint = when (tool) {
                Tool.HEAL -> "Tap a spot to remove it"
                Tool.MASKS -> when (selectedMask?.type) {
                    Mask.BRUSH -> if (brushErase) "Paint to erase from the mask" else "Paint over the area to adjust"
                    Mask.RADIAL, Mask.LINEAR -> "Tap or drag to place the mask"
                    null -> "Add a mask below"
                    else -> null
                }
                Tool.CROP -> "Drag the corners or move the frame"
                else -> null
            }
            if (hint != null && !fullScreen) Pill(hint, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            busy?.let { label ->
                Column(
                    Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(label, color = Color.White)
                }
            }
        }
    }

    // =============================== Top bar ===============================
    val topBar: @Composable () -> Unit = {
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
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit history") }, onClick = { showHistory = true; menuOpen = false })
                    DropdownMenuItem(text = { Text("Copy edit") }, onClick = {
                        EditClipboard.params = params
                        menuOpen = false
                        context.toast("Edit copied")
                    })
                    DropdownMenuItem(text = { Text("Paste edit") }, enabled = EditClipboard.params != null, onClick = {
                        EditClipboard.params?.let { commit(it.copy(spots = emptyList(), masks = emptyList())) }
                        menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Paste look only (keep crop)") }, enabled = EditClipboard.params != null, onClick = {
                        EditClipboard.params?.let {
                            commit(it.withoutGeometry().withAll(params.geometryOnly().values).copy(spots = params.spots, masks = params.masks))
                        }
                        menuOpen = false
                    })
                    DropdownMenuItem(
                        text = { Text("Clipping warning") },
                        leadingIcon = { Checkbox(checked = clip, onCheckedChange = null) },
                        onClick = { clip = !clip },
                    )
                    DropdownMenuItem(text = { Text("Reset all edits") }, onClick = { commit(EditParams()); menuOpen = false })
                }
            }
        }
    }

    // =============================== Tool panel ===============================
    val panel: @Composable () -> Unit = {
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
                Tool.LIGHT -> Column {
                    MiniHistogram(hist)
                    ChipSliderPanel(specsFor(Group.LIGHT), params, { params = it }, { commit(params) })
                }
                Tool.COLOUR -> ChipSliderPanel(specsFor(Group.COLOUR), params, { params = it }, { commit(params) })
                Tool.CURVE -> CurvePanel(params, hist, { params = it }, { commit(params) })
                Tool.PRESENCE -> ChipSliderPanel(specsFor(Group.PRESENCE), params, { params = it }, { commit(params) })
                Tool.GRADING -> ChipSliderPanel(specsFor(Group.GRADING), params, { params = it }, { commit(params) })
                Tool.MASKS -> MasksPanel(
                    params = params,
                    selected = selectedMask,
                    brushSize = brushSize,
                    brushErase = brushErase,
                    onSelect = { selectedMaskId = it },
                    onAdd = { type ->
                        val m = Mask(id = System.currentTimeMillis(), type = type, cy = if (type == Mask.LINEAR) 0.35f else 0.5f)
                        commit(params.copy(masks = params.masks + m))
                        selectedMaskId = m.id
                        if (type == Mask.SUBJECT) context.toast("Finding the subject… the first time downloads a small model")
                    },
                    onChange = { m, done -> updateMask(m, done) },
                    onDelete = { m ->
                        commit(params.copy(masks = params.masks.filter { it.id != m.id }))
                        selectedMaskId = null
                    },
                    onBrushSize = { brushSize = it },
                    onBrushErase = { brushErase = it },
                )
                Tool.HEAL -> HealPanel(
                    size = healSize,
                    count = params.spots.size,
                    onSize = { healSize = it },
                    onUndoSpot = { if (params.spots.isNotEmpty()) commit(params.copy(spots = params.spots.dropLast(1))) },
                    onClear = { commit(params.copy(spots = emptyList())) },
                )
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
                    onRatio = { i ->
                        val g = geoBase
                        val rect = if (g == null || i == 0) floatArrayOf(0f, 0f, 1f, 1f)
                        else Geometry.ratioRect(g.width, g.height, Adjustments.cropRatios[i])
                        cropRect = rect
                        commit(params.withAll(mapOf("crop" to i.toFloat(), "cropL" to rect[0], "cropT" to rect[1], "cropR" to rect[2], "cropB" to rect[3])))
                    },
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

    val tabs: @Composable () -> Unit = {
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

    // =============================== Layout ===============================
    if (landscape && !fullScreen) {
        Row(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            imageArea(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(360.dp).fillMaxHeight().background(Panel)) {
                topBar()
                tabs()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { panel() }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            if (!fullScreen) topBar()
            imageArea(Modifier.weight(1f).fillMaxWidth())
            if (!fullScreen) {
                panel()
                tabs()
            }
        }
    }

    // =============================== Dialogs ===============================
    if (showHistory) {
        // Every step so far (and any undone steps); tap one to jump straight to it.
        val steps = undoStack.toList() + committed + redoStack.toList().reversed()
        val currentIndex = undoStack.size
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Edit history") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    steps.forEachIndexed { i, step ->
                        val label = if (i == 0) "Original" else stepLabel(steps[i - 1], step)
                        Text(
                            "${i}. $label",
                            color = when {
                                i == currentIndex -> Amber
                                i > currentIndex -> Muted
                                else -> Color.White
                            },
                            fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    undoStack.clear()
                                    undoStack.addAll(steps.subList(0, i))
                                    redoStack.clear()
                                    redoStack.addAll(steps.subList(i + 1, steps.size).reversed())
                                    committed = step
                                    params = step
                                    showHistory = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        )
    }

    reeditOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { reeditOffer = null },
            title = { Text("Re-edit this photo?") },
            text = { Text("This photo was made in GrainLab. Open the original with the same edit so you can change it without losing quality?") },
            confirmButton = {
                TextButton(onClick = {
                    val src = offer.first
                    reeditOffer = null
                    if (src != null) {
                        val ok = runCatching { context.contentResolver.openInputStream(src)?.close(); true }.getOrDefault(false)
                        if (ok) {
                            pendingRestore = offer.second
                            sourceUri = src
                        } else context.toast("The original photo is no longer available")
                    }
                }) { Text("Edit original") }
            },
            dismissButton = { TextButton(onClick = { reeditOffer = null }) { Text("Edit this copy") } },
        )
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
            onExport = { format, size, quality, watermark, share ->
                showExport = false
                val w = working ?: return@ExportDialog
                runBusy("Saving…") {
                    val p = params
                    val src = sourceUri
                    val result = withContext(Dispatchers.Default) {
                        val base = BitmapLoader.scaleToMax(w, size.maxSide)
                        var out = Pipeline.render(base, p)
                        if (size.framed) out = SocialExport.frame(out, size)
                        else if (max(out.width, out.height) > size.maxSide) out = BitmapLoader.scaleToMax(out, size.maxSide)
                        if (watermark) out = SocialExport.watermark(out, Settings.watermarkText)
                        val q = if (size.quality > 0) size.quality else quality
                        val f = if (size == ExportSize.WHATSAPP) ExportFormat.JPEG else format
                        val saved = MediaSaver.saveBitmap(context, out, f, q, "GrainLab_${MediaSaver.timestamp()}")
                        if (saved != null) {
                            ExifWriter.write(context, saved, f.mime, ExifWriter.fromUri(context, src), Settings.keepLocation, null, out.width, out.height)
                            EditHistory.recordOutput(context, saved, src, p)
                        }
                        saved?.let { it to f.mime }
                    }
                    if (result == null) context.toast("Could not save")
                    else {
                        context.toast("Saved to Pictures/GrainLab")
                        if (share) shareImage(context, result.first, result.second)
                    }
                }
            },
        )
    }
}

// =============================== Helpers ===============================

/** A short name for what changed between two steps, for the history list. */
private fun stepLabel(prev: EditParams, next: EditParams): String {
    if (prev.filmId != next.filmId) return "Film: " + (FilmLooks.byId(next.filmId)?.name ?: "none")
    if (prev.masks.size != next.masks.size) return if (next.masks.size > prev.masks.size) "Mask added" else "Mask removed"
    if (prev.masks != next.masks) return "Mask adjusted"
    if (prev.spots.size != next.spots.size) return if (next.spots.size > prev.spots.size) "Spot healed" else "Heal undone"
    val changed = (prev.values.keys + next.values.keys).filter { abs(prev[it] - next[it]) > 1e-4f }
    if (changed.isEmpty()) return "No change"
    val names = changed.map { k ->
        when {
            k.startsWith("curve_") -> "Curve"
            k.startsWith("hsl_") -> "HSL"
            k.startsWith("crop") -> "Crop"
            else -> Adjustments.spec(k)?.label ?: k
        }
    }.distinct()
    return when {
        names.size == 1 -> names[0]
        names.size == 2 -> names.joinToString(" and ")
        names.size > 6 -> "Auto fix / template (${names.size} settings)"
        else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
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

/** Brightness histogram (64 bins) of a small copy after the colour settings. */
private fun lumaHistogram(b: Bitmap, lut: FloatArray): IntArray {
    val n = LutBuilder.N
    val px = IntArray(b.width * b.height)
    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    val out = IntArray(64)
    for (c in px) {
        val ri = (AColor.red(c) * (n - 1) + 127) / 255
        val gi = (AColor.green(c) * (n - 1) + 127) / 255
        val bi = (AColor.blue(c) * (n - 1) + 127) / 255
        val i = ((bi * n + gi) * n + ri) * 3
        val l = 0.299f * lut[i] + 0.587f * lut[i + 1] + 0.114f * lut[i + 2]
        out[(l * 63.99f).toInt().coerceIn(0, 63)]++
    }
    return out
}

/** Grey mask -> red, half-transparent overlay. */
private fun tintMask(mask: Bitmap): Bitmap {
    val px = IntArray(mask.width * mask.height)
    mask.getPixels(px, 0, mask.width, 0, 0, mask.width, mask.height)
    for (i in px.indices) {
        val a = (AColor.red(px[i]) * 0.5f).toInt()
        px[i] = AColor.argb(a, 255, 40, 40)
    }
    return Bitmap.createBitmap(px, mask.width, mask.height, Bitmap.Config.ARGB_8888)
}

private fun shareImage(context: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share photo"))
}

@Composable
private fun MiniHistogram(bins: IntArray) {
    Canvas(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
        val mx = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val bw = size.width / bins.size
        bins.forEachIndexed { i, v ->
            val h = size.height * (v / mx)
            drawRect(
                when {
                    i == 0 && v > 0 -> Color(0xFF4F7CFF)
                    i == bins.size - 1 && v > 0 -> Color(0xFFFF5252)
                    else -> Color(0x99FFFFFF)
                },
                topLeft = Offset(i * bw, size.height - h),
                size = Size(bw * 0.9f, h),
            )
        }
    }
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

/** Tone curve: drag the five points of the chosen channel up or down. */
@Composable
private fun CurvePanel(params: EditParams, hist: IntArray, onChange: (EditParams) -> Unit, onDone: () -> Unit) {
    var ch by remember { mutableStateOf('m') }
    var dragIdx by remember { mutableIntStateOf(-1) }
    val colour = when (ch) {
        'r' -> Color(0xFFFF5A5A)
        'g' -> Color(0xFF5AD35A)
        'b' -> Color(0xFF5A8CFF)
        else -> Color.White
    }
    val ys = FloatArray(5) { params[Adjustments.curveKey(ch, it)] }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf('m' to "RGB", 'r' to "Red", 'g' to "Green", 'b' to "Blue").forEach { (c, label) ->
                val changed = (0..4).any { abs(params[Adjustments.curveKey(c, it)] - it / 4f) > 1e-3f }
                ToolChip(label, ch == c, changed) { ch = c }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Reset", color = Amber, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    onChange(params.withAll((0..4).associate { Adjustments.curveKey(ch, it) to it / 4f }))
                    onDone()
                }.padding(8.dp),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .pointerInput(ch, params) {
                    detectDragGestures(
                        onDragStart = { pos -> dragIdx = ((pos.x / size.width) * 4f).roundToInt().coerceIn(0, 4) },
                        onDragEnd = { dragIdx = -1; onDone() },
                        onDragCancel = { dragIdx = -1; onDone() },
                    ) { change, _ ->
                        change.consume()
                        val i = dragIdx
                        if (i >= 0) {
                            val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            onChange(params.with(Adjustments.curveKey(ch, i), y))
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val mx = (hist.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            val bw = w / hist.size
            hist.forEachIndexed { i, v ->
                val bh = h * (v / mx)
                drawRect(Color(0x33FFFFFF), Offset(i * bw, h - bh), Size(bw, bh))
            }
            for (i in 1..3) {
                drawLine(Color(0x22FFFFFF), Offset(w * i / 4f, 0f), Offset(w * i / 4f, h))
                drawLine(Color(0x22FFFFFF), Offset(0f, h * i / 4f), Offset(w, h * i / 4f))
            }
            drawLine(Color(0x33FFFFFF), Offset(0f, h), Offset(w, 0f))
            val curve = Curve(ys)
            val path = Path()
            for (s in 0..64) {
                val x = s / 64f
                val px = x * w
                val py = h - curve.eval(x).coerceIn(0f, 1f) * h
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, colour, style = DrawStroke(width = 3f))
            for (i in 0..4) {
                val c = Offset(w * i / 4f, h - ys[i] * h)
                drawCircle(Color.Black, 10f, c)
                drawCircle(colour, 7f, c)
            }
        }
    }
}

@Composable
private fun MasksPanel(
    params: EditParams,
    selected: Mask?,
    brushSize: Float,
    brushErase: Boolean,
    onSelect: (Long?) -> Unit,
    onAdd: (Int) -> Unit,
    onChange: (Mask, Boolean) -> Unit,
    onDelete: (Mask) -> Unit,
    onBrushSize: (Float) -> Unit,
    onBrushErase: (Boolean) -> Unit,
) {
    var key by remember(selected?.id) { mutableStateOf("exposure") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            params.masks.forEachIndexed { i, m ->
                ToolChip("${i + 1}. ${Mask.typeNames[m.type]}", m.id == selected?.id, true) {
                    onSelect(if (m.id == selected?.id) null else m.id)
                }
            }
            if (params.masks.size < Mask.MAX) {
                Text("Add:", color = Muted, fontSize = 12.sp)
                Mask.typeNames.forEachIndexed { t, name ->
                    Text(
                        "+ $name", color = Amber, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A2622)).clickable { onAdd(t) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
        if (selected == null) {
            Text(
                "Masks adjust only part of the photo: a radial spot, a gradient (great for skies), a brushed area, " +
                    "or the sky or main subject found automatically. Up to 4 masks.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@Column
        }
        val adj = mutableListOf("exposure" to "Exposure", "contrast" to "Contrast", "saturation" to "Saturation", "temperature" to "Warmth")
        when (selected.type) {
            Mask.RADIAL -> adj += listOf("size" to "Size", "feather" to "Feather", "angle" to "Angle")
            Mask.LINEAR -> adj += listOf("feather" to "Softness", "angle" to "Angle")
            Mask.BRUSH -> adj += listOf("brush" to "Brush size", "feather" to "Softness")
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            adj.forEach { (k, label) -> ToolChip(label, key == k, false) { key = k } }
            if (selected.type == Mask.BRUSH) {
                ToolChip(if (brushErase) "Erasing" else "Painting", brushErase, false) { onBrushErase(!brushErase) }
            }
            ToolChip(if (selected.invert) "Inverted" else "Invert", selected.invert, false) { onChange(selected.copy(invert = !selected.invert), true) }
            IconButton(onClick = { onDelete(selected) }) { Icon(Icons.Filled.Delete, "Delete mask", tint = Color.White) }
        }
        val (value, range) = when (key) {
            "exposure" -> selected.exposure to (-2f..2f)
            "contrast" -> selected.contrast to (-1f..1f)
            "saturation" -> selected.saturation to (-1f..1f)
            "temperature" -> selected.temperature to (-1f..1f)
            "size" -> selected.size to (0.05f..1f)
            "feather" -> selected.feather to (0.02f..1f)
            "angle" -> selected.angle to (-90f..90f)
            else -> brushSize to (0.01f..0.2f)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                valueRange = range,
                onValueChange = { v ->
                    when (key) {
                        "exposure" -> onChange(selected.copy(exposure = v), false)
                        "contrast" -> onChange(selected.copy(contrast = v), false)
                        "saturation" -> onChange(selected.copy(saturation = v), false)
                        "temperature" -> onChange(selected.copy(temperature = v), false)
                        "size" -> onChange(selected.copy(size = v), false)
                        "feather" -> onChange(selected.copy(feather = v), false)
                        "angle" -> onChange(selected.copy(angle = v), false)
                        else -> onBrushSize(v)
                    }
                },
                onValueChangeFinished = { onChange(selected, true) },
                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatValue(value, range.start, range.endInclusive), color = Amber, fontSize = 13.sp,
                modifier = Modifier.width(52.dp).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun HealPanel(size: Float, count: Int, onSize: (Float) -> Unit, onUndoSpot: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$count spots removed", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            ToolChip("Undo spot", false, false, onUndoSpot)
            ToolChip("Clear all", false, false, onClear)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Brush", color = Muted, fontSize = 12.sp)
            Slider(
                value = size, onValueChange = onSize, valueRange = 0.008f..0.12f,
                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Box(Modifier.size((8f + size * 200f).coerceAtMost(32f).dp).border(2.dp, Color.White, CircleShape))
        }
    }
}

/** Crop frame with corner handles over the uncropped photo. */
@Composable
private fun CropOverlay(r: FloatArray, rect: FloatArray, params: EditParams, geo: Bitmap?, onRect: (FloatArray, Boolean) -> Unit) {
    var handle by remember { mutableIntStateOf(-1) }
    var current by remember(rect.contentToString()) { mutableStateOf(rect) }
    val ratioIdx = params["crop"].roundToInt()
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(r.contentToString(), ratioIdx) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val c = current
                        val corners = listOf(
                            Offset(r[0] + c[0] * r[2], r[1] + c[1] * r[3]),
                            Offset(r[0] + c[2] * r[2], r[1] + c[1] * r[3]),
                            Offset(r[0] + c[2] * r[2], r[1] + c[3] * r[3]),
                            Offset(r[0] + c[0] * r[2], r[1] + c[3] * r[3]),
                        )
                        val near = corners.withIndex().minByOrNull { hypot(it.value.x - pos.x, it.value.y - pos.y) }
                        handle = if (near != null && hypot(near.value.x - pos.x, near.value.y - pos.y) < 56.dp.toPx()) near.index + 1 else 0
                    },
                    onDragEnd = { onRect(current, true); handle = -1 },
                    onDragCancel = { onRect(current, true); handle = -1 },
                ) { change, drag ->
                    change.consume()
                    val dx = drag.x / r[2]
                    val dy = drag.y / r[3]
                    var l = current[0]
                    var t = current[1]
                    var rr = current[2]
                    var b = current[3]
                    val minS = 0.06f
                    when (handle) {
                        0 -> {
                            val mx = dx.coerceIn(-l, 1f - rr)
                            val my = dy.coerceIn(-t, 1f - b)
                            l += mx; rr += mx; t += my; b += my
                        }
                        1 -> { l = (l + dx).coerceIn(0f, rr - minS); t = (t + dy).coerceIn(0f, b - minS) }
                        2 -> { rr = (rr + dx).coerceIn(l + minS, 1f); t = (t + dy).coerceIn(0f, b - minS) }
                        3 -> { rr = (rr + dx).coerceIn(l + minS, 1f); b = (b + dy).coerceIn(t + minS, 1f) }
                        4 -> { l = (l + dx).coerceIn(0f, rr - minS); b = (b + dy).coerceIn(t + minS, 1f) }
                    }
                    // Keep the chosen ratio while resizing.
                    if (handle in 1..4 && ratioIdx in 1 until Adjustments.cropRatios.size && geo != null) {
                        val ratio = Adjustments.cropRatios[ratioIdx]
                        val target = if (geo.width >= geo.height) max(ratio, 1f / ratio) else min(ratio, 1f / ratio)
                        val hN = ((rr - l) * geo.width / target / geo.height).coerceAtMost(1f)
                        if (handle == 1 || handle == 2) t = (b - hN).coerceAtLeast(0f) else b = (t + hN).coerceAtMost(1f)
                    }
                    current = floatArrayOf(l, t, rr, b)
                    onRect(current, false)
                }
            },
    ) {
        val c = current
        val x0 = r[0] + c[0] * r[2]
        val y0 = r[1] + c[1] * r[3]
        val x1 = r[0] + c[2] * r[2]
        val y1 = r[1] + c[3] * r[3]
        val shade = Color(0x99000000)
        drawRect(shade, Offset(r[0], r[1]), Size(r[2], y0 - r[1]))
        drawRect(shade, Offset(r[0], y1), Size(r[2], r[1] + r[3] - y1))
        drawRect(shade, Offset(r[0], y0), Size(x0 - r[0], y1 - y0))
        drawRect(shade, Offset(x1, y0), Size(r[0] + r[2] - x1, y1 - y0))
        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = DrawStroke(2f))
        for (i in 1..2) {
            drawLine(Color(0x66FFFFFF), Offset(x0 + (x1 - x0) * i / 3f, y0), Offset(x0 + (x1 - x0) * i / 3f, y1))
            drawLine(Color(0x66FFFFFF), Offset(x0, y0 + (y1 - y0) * i / 3f), Offset(x1, y0 + (y1 - y0) * i / 3f))
        }
        val hl = 22.dp.toPx()
        listOf(
            Offset(x0, y0) to Offset(1f, 1f), Offset(x1, y0) to Offset(-1f, 1f),
            Offset(x1, y1) to Offset(-1f, -1f), Offset(x0, y1) to Offset(1f, -1f),
        ).forEach { (p, d) ->
            drawLine(Color.White, p, p + Offset(d.x * hl, 0f), strokeWidth = 6f)
            drawLine(Color.White, p, p + Offset(0f, d.y * hl), strokeWidth = 6f)
        }
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
            if (shown != null) {
                Image(
                    shown.asImageBitmap(), contentDescription = look?.name ?: "Original",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
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
            Adjustments.bands.forEachIndexed { i, (_, hue) ->
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
    onRatio: (Int) -> Unit,
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
                ToolChip(if (i == 0) "Free" else name, params["crop"].roundToInt() == i, false) { onRatio(i) }
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
            Text(
                "Reset crop", color = Amber, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onRatio(0) }.padding(horizontal = 12.dp, vertical = 7.dp),
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
                Text(
                    "Film, light, colour, curve, detail and effects are always included. Masks and healing stay with the photo.",
                    color = Muted, fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim().ifEmpty { "My template" }, includeCrop) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, ExportSize, Int, Boolean, Boolean) -> Unit) {
    val formats = ExportFormat.entries
    val sizes = ExportSize.entries
    var format by remember { mutableStateOf(formats[Settings.exportFormat.coerceIn(0, formats.size - 1)]) }
    var size by remember { mutableStateOf(sizes[Settings.exportSize.coerceIn(0, sizes.size - 1)]) }
    var quality by remember { mutableFloatStateOf(Settings.exportQuality.toFloat().coerceIn(60f, 100f)) }
    var watermark by remember { mutableStateOf(Settings.watermark) }
    var watermarkText by remember { mutableStateOf(Settings.watermarkText) }
    fun go(share: Boolean) {
        Settings.exportFormat = format.ordinal
        Settings.exportSize = size.ordinal
        Settings.exportQuality = quality.roundToInt()
        Settings.watermark = watermark
        Settings.watermarkText = watermarkText
        onExport(format, size, quality.roundToInt(), watermark, share)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Size", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizes.forEach { s -> Choice(s.label, size == s) { size = s } }
                }
                if (size != ExportSize.WHATSAPP) {
                    Text("Format", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        formats.forEach { f -> Choice(f.label, format == f) { format = f } }
                    }
                    if (format != ExportFormat.PNG) {
                        Text("Quality ${quality.roundToInt()}", color = Muted, fontSize = 12.sp)
                        Slider(
                            value = quality, onValueChange = { quality = it }, valueRange = 60f..100f,
                            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { watermark = !watermark }) {
                    Checkbox(checked = watermark, onCheckedChange = { watermark = it })
                    Text("Watermark")
                }
                if (watermark) {
                    OutlinedTextField(value = watermarkText, onValueChange = { watermarkText = it }, singleLine = true, label = { Text("Watermark text") })
                }
                Text("Camera details are kept. Location is added only if turned on in camera settings.", color = Muted, fontSize = 11.sp)
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
