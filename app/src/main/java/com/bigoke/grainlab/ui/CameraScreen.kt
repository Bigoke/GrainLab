package com.bigoke.grainlab.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.FilmLook
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Geometry
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.gl.CameraGlRenderer
import com.bigoke.grainlab.io.BitmapLoader
import com.bigoke.grainlab.io.ExportFormat
import com.bigoke.grainlab.io.MediaSaver
import com.bigoke.grainlab.video.VideoDeveloper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/** What the viewfinder shows: nothing, a film look, or a saved template. */
sealed class Look(val title: String) {
    data object None : Look("Original")
    class Film(val film: FilmLook) : Look(film.name)
    class Tmpl(val template: Template) : Look(template.name)

    val key: String
        get() = when (this) {
            None -> "none"
            is Film -> "f:" + film.id
            is Tmpl -> "t:" + template.id
        }

    fun params(): EditParams = when (this) {
        None -> EditParams()
        is Film -> EditParams().withFilm(film)
        is Tmpl -> template.params
    }
}

private enum class Mode { PHOTO, VIDEO }

private class CamHolder {
    var provider: ProcessCameraProvider? = null
    var camera: Camera? = null
    var imageCapture: ImageCapture? = null
    var videoCapture: VideoCapture<Recorder>? = null
}

private fun Context.granted(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

private fun permissionsToAsk(): Array<String> {
    val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT < 29) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    return list.toTypedArray()
}

@Composable
fun CameraScreen(onOpenEditor: (Uri) -> Unit, onOpenTemplates: () -> Unit, onOpenBatch: () -> Unit) {
    val context = LocalContext.current
    var camGranted by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camGranted = context.granted(Manifest.permission.CAMERA)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onOpenEditor(uri)
    }
    val openGallery = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) { if (!camGranted) permLauncher.launch(permissionsToAsk()) }

    if (!camGranted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("GrainLab", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Amber)
            Spacer(Modifier.height(12.dp))
            Text("Allow the camera to shoot with film looks, or edit a photo from your gallery.", color = Muted)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { permLauncher.launch(permissionsToAsk()) }) { Text("Allow camera") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = openGallery) { Text("Edit a photo") }
            TextButton(onClick = onOpenTemplates) { Text("My templates") }
        }
        return
    }
    CameraView(onOpenEditor, onOpenTemplates, onOpenBatch, openGallery)
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraView(
    onOpenEditor: (Uri) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenBatch: () -> Unit,
    onGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val templates by TemplateStore.items.collectAsState()
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember {
        CameraGlRenderer(glView).also {
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    val cam = remember { CamHolder() }

    var surfaceGen by remember { mutableIntStateOf(0) }
    var contentAspect by remember { mutableFloatStateOf(3f / 4f) }
    var lensBack by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var wide by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timer by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(false) }
    var saveOriginal by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf<Look>(Look.None) }
    var strength by remember { mutableFloatStateOf(1f) }
    var showExposure by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableLongStateOf(0L) }
    var developing by remember { mutableStateOf<Int?>(null) }
    var quality by remember { mutableIntStateOf(1) }
    var menuOpen by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    val effective = remember(look.key, strength) { look.params().scaled(strength) }

    DisposableEffect(renderer) {
        renderer.onSurfaceReady = { surfaceGen++ }
        onDispose { renderer.onSurfaceReady = null }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    recording?.stop()
                    glView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recording?.stop()
            cam.provider?.unbindAll()
            glView.onPause()
        }
    }

    // Push the chosen look's lookup table to the viewfinder.
    LaunchedEffect(effective, surfaceGen) {
        val p = effective
        val lut = withContext(Dispatchers.Default) { Pipeline.lutFor(p, useCache = false) }
        renderer.setLook(p, lut)
    }

    fun provideSurface(request: SurfaceRequest) {
        val res = request.resolution
        request.setTransformationInfoListener(mainExecutor) { info ->
            renderer.rotationDegrees = info.rotationDegrees
            contentAspect = renderer.contentAspect()
        }
        glView.queueEvent {
            val st = renderer.surfaceTexture
            if (st == null) {
                request.willNotProvideSurface()
                return@queueEvent
            }
            st.setDefaultBufferSize(res.width, res.height)
            renderer.bufferWidth = res.width
            renderer.bufferHeight = res.height
            val surface = Surface(st)
            request.provideSurface(surface, mainExecutor) { surface.release() }
            glView.post { contentAspect = renderer.contentAspect() }
        }
    }

    // (Re)bind the camera when the surface, lens, mode or format changes.
    LaunchedEffect(surfaceGen, lensBack, mode, wide, quality) {
        if (surfaceGen == 0) return@LaunchedEffect
        val provider = awaitProvider(context)
        cam.provider = provider
        val ratio = if (wide || mode == Mode.VIDEO) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val selector = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
        val preview = Preview.Builder().setResolutionSelector(selector).build()
        preview.setSurfaceProvider(mainExecutor) { provideSurface(it) }
        val cameraSelector = if (lensBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        renderer.mirror = !lensBack
        try {
            provider.unbindAll()
            if (mode == Mode.PHOTO) {
                val ic = ImageCapture.Builder()
                    .setResolutionSelector(selector)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flash)
                    .build()
                cam.imageCapture = ic
                cam.videoCapture = null
                cam.camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, ic)
            } else {
                val q = listOf(Quality.HD, Quality.FHD, Quality.UHD)[quality]
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val vc = VideoCapture.Builder(recorder)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .build()
                cam.videoCapture = vc
                cam.imageCapture = null
                cam.camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, vc)
            }
            exposureIndex = 0
        } catch (e: Exception) {
            context.toast("Camera error: ${e.message}")
        }
    }

    fun takePhoto() {
        if (busy) return
        scope.launch {
            if (timer > 0) {
                for (i in timer downTo 1) { countdown = i; delay(1000) }
                countdown = 0
            }
            val ic = cam.imageCapture ?: return@launch
            val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
            busy = true
            val ok = suspendCancellableCoroutine<Boolean> { cont ->
                ic.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }
            if (!ok) {
                busy = false
                context.toast("Capture failed")
                return@launch
            }
            val p = effective
            val front = !lensBack
            val keep = saveOriginal
            val result = withContext(Dispatchers.IO) {
                runCatching { developPhoto(context, file, p, front, keep) }.getOrNull()
            }
            file.delete()
            busy = false
            if (result != null) {
                lastUri = result.first
                lastThumb = result.second
            } else context.toast("Could not save the photo")
        }
    }

    fun toggleRecording() {
        val rec = recording
        if (rec != null) {
            rec.stop()
            return
        }
        val vc = cam.videoCapture ?: return
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
        var pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        if (context.granted(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()
        val p = effective
        val lut = if (p.isIdentity()) null else Pipeline.lutFor(p, useCache = false)
        recordSeconds = 0
        paused = false
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status ->
                    recordSeconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    paused = false
                    if (file.exists() && file.length() > 0) {
                        developing = 0
                        VideoDeveloper.develop(
                            context, file, lut,
                            onProgress = { developing = it },
                            onDone = { uri ->
                                developing = null
                                context.toast(if (uri != null) "Video saved to Movies/GrainLab" else "Video could not be saved")
                            },
                        )
                    } else {
                        context.toast("Recording failed")
                    }
                }
                else -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        // Gestures, grid and focus ring over the picture area.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val c = cam.camera ?: return@detectTransformGestures
                        val z = c.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        c.cameraControl.setZoomRatio((z.zoomRatio * zoom).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        val c = cam.camera ?: return@detectTapGestures
                        val vw = size.width.toFloat()
                        val vh = size.height.toFloat()
                        val a = renderer.contentAspect()
                        val cw = if (vw / vh > a) vh * a else vw
                        val ch = if (vw / vh > a) vh else vw / a
                        val left = (vw - cw) / 2f
                        val top = (vh - ch) / 2f
                        if (off.x < left || off.x > left + cw || off.y < top || off.y > top + ch) return@detectTapGestures
                        val display = glView.display ?: return@detectTapGestures
                        val factory = DisplayOrientedMeteringPointFactory(display, c.cameraInfo, cw, ch)
                        val point = factory.createPoint(off.x - left, off.y - top)
                        c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        focusPoint = off
                    }
                },
        ) {
            val vw = constraints.maxWidth.toFloat()
            val vh = constraints.maxHeight.toFloat()
            val a = contentAspect
            val cw = if (vw / vh > a) vh * a else vw
            val ch = if (vw / vh > a) vh else vw / a
            val left = (vw - cw) / 2f
            val top = (vh - ch) / 2f
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = Color(0x66FFFFFF)
                    for (i in 1..2) {
                        val x = left + cw * i / 3f
                        val y = top + ch * i / 3f
                        drawLine(line, Offset(x, top), Offset(x, top + ch), strokeWidth = 1.5f)
                        drawLine(line, Offset(left, y), Offset(left + cw, y), strokeWidth = 1.5f)
                    }
                }
            }
            focusPoint?.let { fp ->
                LaunchedEffect(fp) { delay(900); focusPoint = null }
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Amber, radius = 42f, center = fp, style = Stroke(width = 3f))
                }
            }
            if (countdown > 0) {
                Text(
                    "$countdown",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            // Recording timer sits just above the picture's bottom edge.
            if (recording != null) {
                val y = with(density) { (top + ch).toDp() - 48.dp }
                Pill(
                    (if (paused) "Paused  " else "● ") + String.format(Locale.US, "%02d:%02d", recordSeconds / 60, recordSeconds % 60),
                    Modifier.align(Alignment.TopCenter).offset(y = y),
                )
            }
        }

        // ---- Top bar ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == Mode.PHOTO) {
                IconButton(onClick = {
                    flash = when (flash) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    cam.imageCapture?.flashMode = flash
                }) {
                    Icon(
                        when (flash) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            else -> Icons.Filled.FlashOff
                        },
                        contentDescription = "Flash", tint = Color.White,
                    )
                }
                IconButton(onClick = { timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } }) {
                    Icon(
                        when (timer) { 3 -> Icons.Filled.Timer3; 10 -> Icons.Filled.Timer10; else -> Icons.Filled.TimerOff },
                        contentDescription = "Timer", tint = if (timer > 0) Amber else Color.White,
                    )
                }
                TextButton(onClick = { wide = !wide }) {
                    Text(if (wide) "16:9" else "4:3", color = Color.White)
                }
            } else {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.Transparent, modifier = Modifier.size(24.dp))
                TextButton(
                    onClick = { if (recording == null) quality = (quality + 1) % 3 },
                ) { Text(listOf("HD", "Full HD", "4K")[quality], color = Color.White) }
            }
            IconButton(onClick = { grid = !grid }) {
                Icon(if (grid) Icons.Filled.GridOn else Icons.Filled.GridOff, contentDescription = "Grid", tint = Color.White)
            }
            IconButton(onClick = { showExposure = !showExposure }) {
                Icon(Icons.Filled.Exposure, contentDescription = "Exposure", tint = if (showExposure) Amber else Color.White)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("My templates") },
                        leadingIcon = { Icon(Icons.Filled.Bookmarks, null) },
                        onClick = { menuOpen = false; onOpenTemplates() },
                    )
                    DropdownMenuItem(
                        text = { Text("Batch edit photos") },
                        leadingIcon = { Icon(Icons.Filled.BurstMode, null) },
                        onClick = { menuOpen = false; onOpenBatch() },
                    )
                    DropdownMenuItem(
                        text = { Text("Also save original") },
                        leadingIcon = { Checkbox(checked = saveOriginal, onCheckedChange = null) },
                        onClick = { saveOriginal = !saveOriginal },
                    )
                }
            }
        }

        // ---- Bottom controls ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            if (showExposure) {
                val c = cam.camera
                val state = c?.cameraInfo?.exposureState
                if (state != null && state.isExposureCompensationSupported) {
                    val range = state.exposureCompensationRange
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("EV", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = {
                                exposureIndex = it.toInt()
                                c?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                            },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }
                }
            }
            if (look != Look.None) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(look.title, color = Amber, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(strength * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                }
            }
            LookStrip(templates, look) { look = it; strength = 1f }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                listOf(Mode.PHOTO to "PHOTO", Mode.VIDEO to "VIDEO").forEach { (m, label) ->
                    Text(
                        label,
                        color = if (mode == m) Amber else Color.White,
                        fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(enabled = recording == null && !busy) { mode = m }
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Last shot / gallery
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2724))
                        .clickable {
                            val u = lastUri
                            if (u != null) onOpenEditor(u) else onGallery()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val t = lastThumb
                    if (t != null) {
                        Image(t.asImageBitmap(), contentDescription = "Last photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                    }
                }

                ShutterButton(
                    mode = mode,
                    recording = recording != null,
                    enabled = !busy && developing == null,
                    onClick = { if (mode == Mode.PHOTO) takePhoto() else toggleRecording() },
                )

                if (recording != null) {
                    IconButton(
                        onClick = {
                            val r = recording ?: return@IconButton
                            if (paused) r.resume() else r.pause()
                            paused = !paused
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { lensBack = !lensBack }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onGallery) { Text("Edit a photo", color = Muted, fontSize = 12.sp) }
            }
        }

        if (busy || developing != null) {
            Column(
                Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Amber)
                Spacer(Modifier.height(12.dp))
                val d = developing
                Text(if (d != null) "Developing video… $d%" else "Developing…", color = Color.White)
            }
        }
    }
}

@Composable
private fun ShutterButton(mode: Mode, recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Gray
                    mode == Mode.VIDEO -> Color(0xFFE53935)
                    else -> Color.White
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(22.dp).background(Color.White, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun LookStrip(templates: List<Template>, selected: Look, onSelect: (Look) -> Unit) {
    val looks = remember(templates) {
        buildList<Look> {
            add(Look.None)
            templates.forEach { add(Look.Tmpl(it)) }
            FilmLooks.all.forEach { add(Look.Film(it)) }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        items(looks, key = { it.key }) { l ->
            val isSel = l.key == selected.key
            val sub = when (l) {
                Look.None -> "No filter"
                is Look.Film -> l.film.pack
                is Look.Tmpl -> if (l.template.favourite) "★ Template" else "Template"
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) Amber else Color(0xCC1F1C19))
                    .clickable { onSelect(l) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (l is Look.Tmpl && l.template.favourite) {
                        Icon(Icons.Filled.Star, null, tint = if (isSel) Color.Black else Amber, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(l.title, color = if (isSel) Color(0xFF2B1A00) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(sub, color = if (isSel) Color(0xFF5A3D00) else Muted, fontSize = 10.sp)
            }
        }
    }
}

private suspend fun awaitProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (cont.isActive) cont.resume(future.get())
        }, ContextCompat.getMainExecutor(context))
    }

/** Loads the captured JPEG, applies the look at full size and saves it. */
private suspend fun developPhoto(
    context: Context,
    file: File,
    p: EditParams,
    front: Boolean,
    keepOriginal: Boolean,
): Pair<Uri, Bitmap>? {
    var bmp = BitmapLoader.load(context, Uri.fromFile(file), 4096) ?: return null
    if (front) {
        val m = Geometry.mirror(bmp)
        if (m !== bmp) bmp.recycle()
        bmp = m
    }
    val ts = MediaSaver.timestamp()
    if (keepOriginal) MediaSaver.saveBitmap(context, bmp, ExportFormat.JPEG, 95, "GrainLab_${ts}_original")
    val seed = abs(ts.hashCode() % 1000) / 1000f
    val out = if (p.isIdentity()) bmp else Pipeline.render(bmp, p, useCache = false, seed = seed)
    val uri = MediaSaver.saveBitmap(context, out, ExportFormat.JPEG, 95, "GrainLab_$ts") ?: return null
    return uri to BitmapLoader.scaleToMax(out, 256)
}
