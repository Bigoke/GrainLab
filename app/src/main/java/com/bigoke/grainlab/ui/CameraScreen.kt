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
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.bigoke.grainlab.camera.CameraReport
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaActionSound
import android.util.Range
import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import com.bigoke.grainlab.camera.CameraKeys
import com.bigoke.grainlab.camera.Scene
import com.bigoke.grainlab.camera.SceneAnalyzer
import com.bigoke.grainlab.camera.Manual
import com.bigoke.grainlab.camera.ManualRanges
import com.bigoke.grainlab.camera.ProCamera
import com.bigoke.grainlab.camera.rememberRoll
import java.util.concurrent.Executors
import com.bigoke.grainlab.data.Lens
import com.bigoke.grainlab.data.LensCatalog
import com.bigoke.grainlab.data.Settings
import com.bigoke.grainlab.data.Template
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.FilmLook
import com.bigoke.grainlab.edit.FilmLooks
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.gl.CameraGlRenderer
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

    companion object {
        fun fromKey(key: String, templates: List<Template>): Look = when {
            key.startsWith("f:") -> FilmLooks.byId(key.removePrefix("f:"))?.let { Film(it) } ?: None
            key.startsWith("t:") -> templates.firstOrNull { it.id == key.removePrefix("t:") }?.let { Tmpl(it) } ?: None
            else -> None
        }
    }
}

private enum class Mode { PHOTO, VIDEO }

private const val MINE = "My templates"

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

private fun lookList(templates: List<Template>, packFilter: String?): List<Look> = buildList {
    add(Look.None)
    if (packFilter == null || packFilter == MINE) templates.forEach { add(Look.Tmpl(it)) }
    if (packFilter != MINE) FilmLooks.all.filter { packFilter == null || it.pack == packFilter }.forEach { add(Look.Film(it)) }
}

@Composable
fun CameraScreen(onOpenEditor: (Uri) -> Unit, onOpenTemplates: () -> Unit, onOpenBatch: () -> Unit, onOpenGallery: () -> Unit) {
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
    CameraView(onOpenEditor, onOpenTemplates, onOpenBatch, openGallery, onOpenGallery)
}

@OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("MissingPermission")
@Composable
private fun CameraView(
    onOpenEditor: (Uri) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenBatch: () -> Unit,
    onGallery: () -> Unit,
    onOpenGallery: () -> Unit,
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

    // Reserve the status bar plus the top bar so the picture sits right under the controls.
    val topInsetPx = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.toPx() }
    SideEffect { renderer.topInsetPx = topInsetPx }

    // ---- Remembered settings ----
    var lensBack by remember { mutableStateOf(Settings.lensBack) }
    var mode by remember { mutableStateOf(if (Settings.videoMode) Mode.VIDEO else Mode.PHOTO) }
    var wide by remember { mutableStateOf(Settings.wide) }
    var flash by remember { mutableIntStateOf(Settings.flash) }
    var timer by remember { mutableIntStateOf(Settings.timer) }
    var grid by remember { mutableStateOf(Settings.grid) }
    var saveOriginal by remember { mutableStateOf(Settings.saveOriginal) }
    var quality by remember { mutableIntStateOf(Settings.videoQuality) }
    var packFilter by remember { mutableStateOf(Settings.packFilter) }
    var look by remember { mutableStateOf(Look.fromKey(Settings.lookKey, TemplateStore.items.value)) }
    var strength by remember { mutableFloatStateOf(Settings.lookStrength) }
    var lensId by remember { mutableStateOf(Settings.lensId ?: "z1") }

    LaunchedEffect(lensBack, mode, wide, flash, timer, grid, saveOriginal, quality, packFilter, look.key, strength, lensId) {
        Settings.lensBack = lensBack
        Settings.videoMode = mode == Mode.VIDEO
        Settings.wide = wide
        Settings.flash = flash
        Settings.timer = timer
        Settings.grid = grid
        Settings.saveOriginal = saveOriginal
        Settings.videoQuality = quality
        Settings.packFilter = packFilter
        Settings.lookKey = look.key
        Settings.lookStrength = strength
        Settings.lensId = lensId
    }

    // ---- Session state ----
    var surfaceGen by remember { mutableIntStateOf(0) }
    var contentAspect by remember { mutableFloatStateOf(3f / 4f) }
    var lenses by remember { mutableStateOf<List<Lens>>(emptyList()) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showLooks by remember { mutableStateOf(false) }
    var lookToast by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumb by remember { mutableStateOf<Bitmap?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableLongStateOf(0L) }
    var developing by remember { mutableStateOf<Int?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var flashOverlay by remember { mutableStateOf(false) }

    // ---- Pro and feedback settings (remembered) ----
    var shutterSound by remember { mutableStateOf(Settings.shutterSound) }
    var showHistogram by remember { mutableStateOf(Settings.histogram) }
    var peaking by remember { mutableStateOf(Settings.peaking) }
    var zebra by remember { mutableStateOf(Settings.zebra) }
    var cine24 by remember { mutableStateOf(Settings.cine24) }
    var stabilise by remember { mutableStateOf(Settings.stabilise) }
    var night by remember { mutableStateOf(Settings.night) }
    var captureMp by remember { mutableFloatStateOf(0f) }
    var ai by remember { mutableStateOf(Settings.ai) }
    var ignoredScene by remember { mutableStateOf(Scene.NONE) }
    LaunchedEffect(ai) { Settings.ai = ai }
    var proMode by remember { mutableStateOf(Settings.proMode) }
    var level by remember { mutableStateOf(Settings.level) }
    var keepLocation by remember { mutableStateOf(Settings.keepLocation) }
    var safeNames by remember { mutableStateOf(Settings.storeSafeNames) }
    LaunchedEffect(shutterSound, showHistogram, peaking, zebra, cine24, stabilise, night, proMode, level, keepLocation, safeNames) {
        Settings.shutterSound = shutterSound
        Settings.histogram = showHistogram
        Settings.peaking = peaking
        Settings.zebra = zebra
        Settings.cine24 = cine24
        Settings.stabilise = stabilise
        Settings.night = night
        Settings.proMode = proMode
        Settings.level = level
        Settings.keepLocation = keepLocation
        Settings.storeSafeNames = safeNames
        FilmLooks.safeNames = safeNames
    }
    SideEffect {
        renderer.peaking = peaking
        renderer.zebra = zebra
    }

    var manual by remember { mutableStateOf(Manual()) }
    var ranges by remember { mutableStateOf<ManualRanges?>(null) }
    var proTab by remember { mutableStateOf("iso") }
    var bursting by remember { mutableStateOf(false) }
    var burstCount by remember { mutableIntStateOf(0) }
    var nightProgress by remember { mutableStateOf<Int?>(null) }
    val analyzer = remember { SceneAnalyzer() }
    val detected by analyzer.scene.collectAsState()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val histBins by analyzer.bins.collectAsState()
    val roll by rememberRoll(level)
    val sound = remember { MediaActionSound().also { it.load(MediaActionSound.SHUTTER_CLICK) } }
    val view = LocalView.current
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var locGranted by remember { mutableStateOf(context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locGranted = context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!locGranted) keepLocation = false
    }

    // Keep a recent location only while "save location" is on.
    DisposableEffect(keepLocation, locGranted) {
        if (!keepLocation || !locGranted) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(LocationManager::class.java) ?: return@DisposableEffect onDispose { }
        val listener = LocationListener { lastLocation = it }
        runCatching {
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                val l = lm.getLastKnownLocation(prov)
                if (l != null && (lastLocation == null || l.time > (lastLocation?.time ?: 0L))) lastLocation = l
            }
            val prov = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestLocationUpdates(prov, 30_000L, 25f, listener, android.os.Looper.getMainLooper())
        }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }

    DisposableEffect(Unit) {
        CameraKeys.active = true
        onDispose {
            CameraKeys.active = false
            analysisExecutor.shutdown()
            analyzer.close()
            sound.release()
        }
    }

    // Manual controls go back to automatic when pro mode is turned off.
    LaunchedEffect(proMode) { if (!proMode) manual = Manual() }

    val pendingShots by PhotoDeveloper.pending.collectAsState()
    val lastShot by PhotoDeveloper.last.collectAsState()
    LaunchedEffect(lastShot) { lastShot?.let { lastUri = it.first; lastThumb = it.second } }
    val shotErrors by PhotoDeveloper.errors.collectAsState()
    LaunchedEffect(shotErrors) { if (shotErrors > 0) context.toast("A photo could not be saved") }

    val looks = remember(templates, packFilter) { lookList(templates, packFilter) }
    val activeScene = if (ai && mode == Mode.PHOTO && detected != ignoredScene) detected else Scene.NONE
    SideEffect { analyzer.aiEnabled = ai && mode == Mode.PHOTO }
    LaunchedEffect(detected) { if (detected != ignoredScene) ignoredScene = Scene.NONE }
    val effective = remember(look.key, strength, activeScene) { activeScene.tune(look.params().scaled(strength)) }
    val currentLens = lenses.firstOrNull { it.id == lensId }
    val bindCameraId = if (lensBack) currentLens?.cameraId else null
    var showReport by remember { mutableStateOf(false) }
    val lastSize by PhotoDeveloper.lastSize.collectAsState()

    LaunchedEffect(recording, paused) {
        if (recording != null && !paused) {
            while (true) {
                delay(1000)
                recordSeconds += 1
            }
        }
    }
    LaunchedEffect(lookToast) {
        if (lookToast != null) { delay(1100); lookToast = null }
    }

    DisposableEffect(renderer) {
        renderer.onSurfaceReady = { surfaceGen++ }
        renderer.onAspectChanged = { contentAspect = it }
        onDispose {
            renderer.onSurfaceReady = null
            renderer.onAspectChanged = null
        }
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

    LaunchedEffect(effective, surfaceGen) {
        val p = effective
        val lut = withContext(Dispatchers.Default) { Pipeline.lutFor(p, useCache = false) }
        renderer.setLook(p, lut)
    }

    fun setZoom(z: Float) {
        val c = cam.camera ?: return
        val st = c.cameraInfo.zoomState.value
        val clamped = if (st != null) z.coerceIn(st.minZoomRatio, st.maxZoomRatio) else z
        c.cameraControl.setZoomRatio(clamped)
        zoomRatio = clamped
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
            renderer.resetAspect()
            val surface = Surface(st)
            request.provideSurface(surface, mainExecutor) { surface.release() }
        }
    }

    // ---- Bind the camera ----
    LaunchedEffect(surfaceGen, lensBack, mode, wide, quality, bindCameraId, showHistogram, cine24, stabilise, night, ai) {
        if (surfaceGen == 0) return@LaunchedEffect
        val provider = awaitProvider(context)
        cam.provider = provider
        val ratio = if (wide || mode == Mode.VIDEO) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val selector = ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build()
        val captureSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(ratio)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector).build()
        preview.setSurfaceProvider(mainExecutor) { provideSurface(it) }
        val cameraSelector = when {
            !lensBack -> CameraSelector.DEFAULT_FRONT_CAMERA
            bindCameraId != null -> CameraSelector.Builder()
                .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == bindCameraId } }
                .build()
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        renderer.mirror = !lensBack
        // Zero-shutter-lag keeps a short buffer of frames so the photo is taken the instant you tap.
        val zsl = runCatching { provider.getCameraInfo(cameraSelector).isZslSupported }.getOrDefault(false)
        val analysis = if (showHistogram || (ai && mode == Mode.PHOTO)) ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) } else null
        try {
            provider.unbindAll()
            val main = if (mode == Mode.PHOTO) {
                val ic = ImageCapture.Builder()
                    .setResolutionSelector(captureSelector)
                    .setCaptureMode(
                        when {
                            zsl && !night -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                            else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        },
                    )
                    .setFlashMode(flash)
                    .build()
                cam.imageCapture = ic
                cam.videoCapture = null
                ic
            } else {
                val q = listOf(Quality.HD, Quality.FHD, Quality.UHD)[quality.coerceIn(0, 2)]
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val vb = VideoCapture.Builder(recorder)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .setVideoStabilizationEnabled(stabilise)
                if (cine24) vb.setTargetFrameRate(Range(24, 24))
                val vc = vb.build()
                cam.videoCapture = vc
                cam.imageCapture = null
                vc
            }
            cam.camera = try {
                if (analysis != null) provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, main, analysis)
                else provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, main)
            } catch (e: Exception) {
                // Some phones cannot run the histogram alongside capture; drop it rather than fail.
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, main)
            }
            exposureIndex = 0
            cam.camera?.let { c ->
                val r = ManualRanges.read(c)
                ranges = r
                if (!manual.isAuto) ProCamera.apply(c, manual, r)
                val res = cam.imageCapture?.resolutionInfo?.resolution
                captureMp = if (res != null) res.width * res.height / 1_000_000f else 0f
            }
        } catch (e: Exception) {
            // A separate lens that cannot be opened falls back to the main camera.
            if (bindCameraId != null) {
                lensId = "z1"
                context.toast("That lens is not available to apps on this phone")
            } else context.toast("Camera error: ${e.message}")
            return@LaunchedEffect
        }

        // Wait for the zoom range, then build the lens buttons once and restore the zoom.
        val c = cam.camera ?: return@LaunchedEffect
        var zs = c.cameraInfo.zoomState.value
        var tries = 0
        while (zs == null && tries < 20) { delay(100); zs = c.cameraInfo.zoomState.value; tries++ }
        val range = LensCatalog.zoomRange(context) ?: zs?.let { it.minZoomRatio to it.maxZoomRatio }
        if (lensBack && bindCameraId == null && lenses.isEmpty() && range != null) {
            val built = withContext(Dispatchers.Default) {
                LensCatalog.backLenses(context, range.first, range.second)
            }
            lenses = built
            if (built.none { it.id == lensId }) lensId = "z1"
        }
        val target = if (lensBack) (lenses.firstOrNull { it.id == lensId }?.zoom ?: 1f) else 1f
        setZoom(target)
    }

    fun selectLens(l: Lens) {
        val sameCamera = l.cameraId == bindCameraId
        lensId = l.id
        if (sameCamera) setZoom(l.zoom)
        // A different camera id rebinds through bindCameraId; the zoom is set after binding.
    }

    fun changeLook(step: Int) {
        if (looks.isEmpty()) return
        val idx = looks.indexOfFirst { it.key == look.key }
        val next = looks[((if (idx < 0) 0 else idx) + step + looks.size) % looks.size]
        look = next
        strength = 1f
        lookToast = next.title
    }

    fun feedback() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (shutterSound) runCatching { sound.play(MediaActionSound.SHUTTER_CLICK) }
    }

    suspend fun captureOne(ic: ImageCapture): Pair<ByteArray, Int>? =
        suspendCancellableCoroutine { cont ->
            ic.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = runCatching {
                            val buf = image.planes[0].buffer
                            buf.rewind()
                            val bytes = ByteArray(buf.remaining())
                            buf.get(bytes)
                            bytes to image.imageInfo.rotationDegrees
                        }.getOrNull()
                        image.close()
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }

    fun takePhoto() {
        if (countdown > 0 || nightProgress != null) return
        scope.launch {
            if (timer > 0) {
                for (i in timer downTo 1) { countdown = i; delay(1000) }
                countdown = 0
            }
            val ic = cam.imageCapture ?: return@launch
            val loc = if (keepLocation) lastLocation else null
            if (night || activeScene == Scene.NIGHT) {
                // Night mode: several frames, then align and average them.
                feedback()
                val frames = ArrayList<Pair<ByteArray, Int>>()
                for (i in 1..6) {
                    nightProgress = i
                    captureOne(ic)?.let { frames.add(it) }
                }
                nightProgress = null
                if (frames.isEmpty()) { context.toast("Capture failed"); return@launch }
                PhotoDeveloper.submitNight(context, frames, effective, mirror = !lensBack, keepOriginal = saveOriginal, location = loc)
                return@launch
            }
            flashOverlay = true
            feedback()
            val shot = captureOne(ic)
            flashOverlay = false
            if (shot == null) {
                context.toast("Capture failed")
                return@launch
            }
            PhotoDeveloper.submit(context, shot.first, shot.second, effective, mirror = !lensBack, keepOriginal = saveOriginal, location = loc)
        }
    }

    /** Burst: keeps shooting while the shutter is held (up to 30 frames). */
    fun startBurst() {
        if (bursting || night) return
        val ic = cam.imageCapture ?: return
        bursting = true
        burstCount = 0
        scope.launch {
            val p = effective
            val loc = if (keepLocation) lastLocation else null
            while (bursting && burstCount < 30) {
                val shot = captureOne(ic) ?: break
                feedback()
                burstCount++
                PhotoDeveloper.submit(context, shot.first, shot.second, p, mirror = !lensBack, keepOriginal = false, location = loc)
            }
            bursting = false
        }
    }

    fun toggleRecording() {
        val rec = recording
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (shutterSound) runCatching {
            sound.play(if (rec != null) MediaActionSound.STOP_VIDEO_RECORDING else MediaActionSound.START_VIDEO_RECORDING)
        }
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

    val shutterAction by rememberUpdatedState({ if (mode == Mode.PHOTO) takePhoto() else toggleRecording() })
    LaunchedEffect(Unit) { CameraKeys.presses.collect { shutterAction() } }
    LaunchedEffect(manual, ranges, activeScene == Scene.MOTION) {
        val c = cam.camera ?: return@LaunchedEffect
        val r = ranges ?: return@LaunchedEffect
        ProCamera.apply(c, manual, r, fast = activeScene == Scene.MOTION)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        // ---- Viewfinder gestures: tap to focus, pinch to zoom, swipe to change look ----
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(looks, topInsetPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var multi = false
                        var moved = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                multi = true
                                val z = event.calculateZoom()
                                if (z != 1f) setZoom(zoomRatio * z)
                                event.changes.forEach { it.consume() }
                            } else if (!multi) {
                                val d = event.changes.first().positionChange()
                                totalX += d.x
                                totalY += d.y
                                if (abs(totalX) > viewConfiguration.touchSlop || abs(totalY) > viewConfiguration.touchSlop) moved = true
                            }
                        } while (event.changes.any { it.pressed })
                        if (!multi) {
                            if (moved && abs(totalX) > 60.dp.toPx() && abs(totalX) > abs(totalY) * 1.5f) {
                                changeLook(if (totalX < 0) 1 else -1)
                            } else if (!moved) {
                                if (showSettings || showLooks) {
                                    showSettings = false
                                    showLooks = false
                                } else {
                                    val c = cam.camera
                                    val rect = CameraGlRenderer.contentRect(size.width.toFloat(), size.height.toFloat(), renderer.contentAspect(), topInsetPx)
                                    val p = down.position
                                    val inside = p.x >= rect[0] && p.x <= rect[0] + rect[2] && p.y >= rect[1] && p.y <= rect[1] + rect[3]
                                    val display = glView.display
                                    if (c != null && inside && display != null) {
                                        val factory = DisplayOrientedMeteringPointFactory(display, c.cameraInfo, rect[2], rect[3])
                                        val point = factory.createPoint(p.x - rect[0], p.y - rect[1])
                                        c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                                        focusPoint = p
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val rect = CameraGlRenderer.contentRect(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), contentAspect, topInsetPx)
            val left = rect[0]; val top = rect[1]; val cw = rect[2]; val ch = rect[3]
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = Color(0x55FFFFFF)
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
            val centreY = with(density) { (top + ch / 2f).toDp() }
            if (countdown > 0) {
                Text(
                    "$countdown",
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = centreY - 60.dp),
                    fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            lookToast?.let {
                Pill(it, Modifier.align(Alignment.TopCenter).offset(y = centreY - 16.dp))
            }
            // Horizon level: a line that turns green when the phone is straight.
            if (level) {
                val straight = abs(roll) < 1f
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = centreY)
                        .rotate(-roll)
                        .width(140.dp)
                        .height(2.dp)
                        .background(if (straight) Color(0xFF4CAF50) else Color(0xCCFFFFFF)),
                )
            }
            // Live histogram in the picture's top-left corner.
            if (showHistogram) {
                val topDp = with(density) { top.toDp() }
                val leftDp = with(density) { left.toDp() }
                Canvas(
                    Modifier
                        .offset(x = leftDp + 8.dp, y = topDp + 8.dp)
                        .size(width = 112.dp, height = 48.dp)
                        .background(Color(0x88000000), RoundedCornerShape(6.dp)),
                ) {
                    val bins = histBins
                    val mx = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                    val bw = size.width / bins.size
                    bins.forEachIndexed { i, v ->
                        val h = size.height * (v / mx)
                        drawRect(
                            if (i >= 61) Color(0xFFFF7043) else Color(0xDDFFFFFF),
                            topLeft = Offset(i * bw, size.height - h),
                            size = androidx.compose.ui.geometry.Size(bw, h),
                        )
                    }
                }
            }
            if (activeScene != Scene.NONE) {
                val topDp = with(density) { top.toDp() }
                Pill(
                    "AI · ${activeScene.label}  ✕",
                    Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = topDp + 8.dp).clickable { ignoredScene = activeScene },
                )
            }
            nightProgress?.let { Pill("Night mode: hold still… $it/6", Modifier.align(Alignment.TopCenter).offset(y = centreY + 30.dp)) }
            if (bursting) Pill("Burst $burstCount", Modifier.align(Alignment.TopCenter).offset(y = centreY + 30.dp))
        }

        if (flashOverlay) Box(Modifier.fillMaxSize().background(Color(0x99FFFFFF)))

        // ---- Top bar: only flash, format and settings ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
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
                TextButton(onClick = { wide = !wide }) { Text(if (wide) "16:9" else "4:3", color = Color.White) }
            } else {
                TextButton(onClick = { if (recording == null) quality = (quality + 1) % 3 }) {
                    Text(listOf("HD", "FHD", "4K")[quality.coerceIn(0, 2)], color = Color.White)
                }
            }
            if (mode == Mode.PHOTO) {
                IconButton(onClick = { night = !night }) {
                    Icon(Icons.Filled.NightsStay, contentDescription = "Night mode", tint = if (night) Amber else Color.White)
                }
            }
            Text(
                "PRO",
                color = if (proMode) Color(0xFF2B1A00) else Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (proMode) Amber else Color(0x33FFFFFF))
                    .clickable { proMode = !proMode }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (mode == Mode.PHOTO) {
                Text(
                    "AI",
                    color = if (ai) Color(0xFF2B1A00) else Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ai) Amber else Color(0x33FFFFFF))
                        .clickable { ai = !ai; ignoredScene = Scene.NONE }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (timer > 0) Text("${timer}s", color = Amber, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            if (recording != null) {
                Row(
                    Modifier
                        .background(if (paused) Color(0xCC444444) else Color(0xDDD32F2F), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!paused) Box(Modifier.size(9.dp).background(Color.White, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (paused) "PAUSED " else "") + String.format(Locale.US, "%02d:%02d", recordSeconds / 60, recordSeconds % 60),
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = { showSettings = !showSettings; showLooks = false }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = if (showSettings) Amber else Color.White)
            }
        }

        // ---- Settings panel ----
        if (showSettings) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .background(Color(0xEE16140F), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingRow("Timer") {
                    listOf(0, 3, 10).forEach { t -> Choice(if (t == 0) "Off" else "${t}s", timer == t) { timer = t } }
                }
                SwitchRow("Grid", grid) { grid = it }
                SwitchRow("Level", level) { level = it }
                SwitchRow("Live histogram", showHistogram) { showHistogram = it }
                SwitchRow("Focus peaking", peaking) { peaking = it }
                SwitchRow("Highlight zebras", zebra) { zebra = it }
                SwitchRow("Shutter sound", shutterSound) { shutterSound = it }
                SwitchRow("Save location in photos", keepLocation) { on ->
                    keepLocation = on
                    if (on && !locGranted) locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                SwitchRow("Brand-free filter names", safeNames) { safeNames = it }
                if (mode == Mode.VIDEO) {
                    SwitchRow("Cinematic 24 fps", cine24) { cine24 = it }
                    SwitchRow("Stabilisation", stabilise) { stabilise = it }
                }
                if (mode == Mode.PHOTO) {
                    SwitchRow("AI scene detection", ai) { ai = it; ignoredScene = Scene.NONE }
                    Text(
                        "Recognises night, moving subjects, text, food, portraits, pets, plants and landscapes, and tunes the photo to suit. Runs on the phone.",
                        color = Muted, fontSize = 11.sp,
                    )
                    TextButton(onClick = { showReport = true }) { Text("Camera report (what this phone allows)") }
                    SwitchRow("Also save the original", saveOriginal) { saveOriginal = it }
                }
                if (mode == Mode.VIDEO) SettingRow("Video") {
                    listOf("HD", "Full HD", "4K").forEachIndexed { i, q -> Choice(q, quality == i) { if (recording == null) quality = i } }
                }
                val state = cam.camera?.cameraInfo?.exposureState
                if (state != null && state.isExposureCompensationSupported) {
                    val range = state.exposureCompensationRange
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Exposure", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = {
                                exposureIndex = it.toInt()
                                cam.camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                            },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSettings = false; onOpenTemplates() }) { Text("My templates") }
                    OutlinedButton(onClick = { showSettings = false; onOpenBatch() }) { Text("Batch edit") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSettings = false; onOpenGallery() }) { Text("GrainLab gallery") }
                    OutlinedButton(onClick = { showSettings = false; onGallery() }) { Text("Edit a photo") }
                }
            }
        }

        // ---- Bottom controls ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLooks) {
                Column(Modifier.fillMaxWidth().background(Color(0xDD0E0D0B)).padding(vertical = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (listOf<String?>(null, MINE) + FilmLooks.packs).forEach { f ->
                            val sel = packFilter == f
                            Text(
                                FilmLooks.packLabel(f),
                                color = if (sel) Color(0xFF2B1A00) else Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (sel) Amber else Color(0x44FFFFFF))
                                    .clickable { packFilter = f }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    LookStrip(looks, look) { look = it; strength = 1f }
                    if (look != Look.None) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Strength", color = Muted, fontSize = 12.sp)
                            Slider(
                                value = strength,
                                onValueChange = { strength = it },
                                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text("${(strength * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Pro controls: ISO, shutter, focus, white balance
            val r = ranges
            if (proMode && r != null) {
                ProPanel(manual, r, proTab, { proTab = it }) { manual = it }
            }

            // Lens buttons
            if (lensBack && lenses.size > 1) {
                Row(
                    Modifier
                        .padding(vertical = 6.dp)
                        .background(Color(0x66000000), RoundedCornerShape(50))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val active = activeLens(lenses, currentLens, zoomRatio)
                    lenses.forEach { l ->
                        val sel = l.id == active?.id
                        val label = if (sel && l.cameraId == null && abs(zoomRatio - l.zoom) > 0.05f) LensCatalog.label(zoomRatio) else l.label
                        Box(
                            Modifier
                                .size(if (sel) 44.dp else 38.dp)
                                .clip(CircleShape)
                                .background(if (sel) Color(0xCC000000) else Color(0x55000000))
                                .clickable { selectLens(l) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (sel) Amber else Color.White, fontSize = if (sel) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (zoomRatio > 1.05f) {
                Pill(LensCatalog.label(zoomRatio), Modifier.padding(vertical = 6.dp))
            }

            // Current look chip: tap to open the looks drawer
            Row(
                Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (look == Look.None) Color(0x66000000) else Color(0xCC000000))
                    .clickable { showLooks = !showLooks; showSettings = false }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (look == Look.None) "Looks" else look.title,
                    color = if (look == Look.None) Color.White else Amber,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
                )
                Icon(if (showLooks) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Photo / video
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                listOf(Mode.PHOTO to "PHOTO", Mode.VIDEO to "VIDEO").forEach { (m, label) ->
                    Text(
                        label,
                        color = if (mode == m) Amber else Color.White,
                        fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(enabled = recording == null) { mode = m }
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
            }

            // Shutter row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    if (pendingShots > 0) {
                        CircularProgressIndicator(color = Amber, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    }
                }

                ShutterButton(
                    video = mode == Mode.VIDEO,
                    recording = recording != null,
                    enabled = developing == null && countdown == 0 && nightProgress == null,
                    onClick = { if (mode == Mode.PHOTO) takePhoto() else toggleRecording() },
                    onHoldStart = { if (mode == Mode.PHOTO) startBurst() },
                    onHoldEnd = { bursting = false },
                )

                if (recording != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2724))
                                .clickable {
                                    val r = recording ?: return@clickable
                                    if (paused) r.resume() else r.pause()
                                    paused = !paused
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
                        }
                        Text(if (paused) "Resume" else "Pause", color = Color.White, fontSize = 11.sp)
                    }
                } else {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2A2724)).clickable { lensBack = !lensBack },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                    }
                }
            }
        }

        if (showReport) {
            val report = remember(captureMp, lensId) {
                CameraReport.build(
                    context,
                    "Now: lens $lensId, shooting ${captureMp.toInt()} MP" +
                        (lastSize?.let { ", last saved ${it.first}x${it.second}" } ?: ""),
                )
            }
            AlertDialog(
                onDismissRequest = { showReport = false },
                title = { Text("Camera report") },
                text = { Text(report, fontSize = 11.sp, modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) },
                confirmButton = {
                    TextButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share camera report"))
                    }) { Text("Share") }
                },
                dismissButton = { TextButton(onClick = { showReport = false }) { Text("Close") } },
            )
        }

        if (developing != null) {
            Column(
                Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Amber)
                Spacer(Modifier.height(12.dp))
                Text("Developing video… ${developing ?: 0}%", color = Color.White)
            }
        }
    }
}

/** The lens button to highlight for the current zoom. */
private fun activeLens(lenses: List<Lens>, current: Lens?, zoom: Float): Lens? {
    if (current?.cameraId != null) return current
    return lenses.filter { it.cameraId == null }.minByOrNull { abs(kotlin.math.ln(it.zoom / zoom)) } ?: current
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Amber))
    }
}

@Composable
private fun ShutterButton(
    video: Boolean,
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    // Tap to shoot; hold (photo mode) for burst.
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, if (held) Amber else Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Gray
                    video -> Color(0xFFE53935)
                    else -> Color.White
                },
            )
            .pointerInput(enabled, video) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        held = false
                        coroutineScope {
                            val job = launch {
                                delay(450)
                                if (!video) { held = true; onHoldStart() }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                        if (held) onHoldEnd()
                    },
                    onTap = {
                        if (enabled && !held) onClick()
                        held = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(22.dp).background(Color.White, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun ProPanel(m: Manual, r: ManualRanges, tab: String, onTab: (String) -> Unit, onChange: (Manual) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xCC0E0D0B)).padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (r.manualSensor) {
                ProChip("ISO", m.iso?.toString() ?: "Auto", tab == "iso") { onTab("iso") }
                ProChip("Shutter", m.shutterNs?.let { ProCamera.shutterLabel(it) } ?: "Auto", tab == "shutter") { onTab("shutter") }
            }
            if (r.minFocusDioptre > 0f) ProChip("Focus", m.focus?.let { ProCamera.focusLabel(it) } ?: "AF", tab == "focus") { onTab("focus") }
            ProChip("WB", ProCamera.wbModes.firstOrNull { it.first == m.wb }?.second ?: "Auto", tab == "wb") { onTab("wb") }
            if (!m.isAuto) {
                Text(
                    "Reset", color = Amber, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onChange(Manual()) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val sliderColors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (tab) {
                "iso" -> if (r.manualSensor) {
                    TextButton(onClick = { onChange(m.copy(iso = null)) }) { Text("A", color = if (m.iso == null) Amber else Color.White) }
                    Slider(
                        value = ProCamera.toSlider((m.iso ?: 200).toDouble(), r.isoMin.toDouble(), r.isoMax.toDouble()),
                        onValueChange = { onChange(m.copy(iso = ProCamera.isoFromSlider(it, r))) },
                        colors = sliderColors, modifier = Modifier.weight(1f),
                    )
                }
                "shutter" -> if (r.manualSensor) {
                    TextButton(onClick = { onChange(m.copy(shutterNs = null)) }) { Text("A", color = if (m.shutterNs == null) Amber else Color.White) }
                    Slider(
                        value = ProCamera.toSlider((m.shutterNs ?: 16_666_667L).toDouble(), r.shutterMinNs.toDouble(), r.shutterMaxNs.toDouble()),
                        onValueChange = { onChange(m.copy(shutterNs = ProCamera.shutterFromSlider(it, r))) },
                        colors = sliderColors, modifier = Modifier.weight(1f),
                    )
                }
                "focus" -> {
                    TextButton(onClick = { onChange(m.copy(focus = null)) }) { Text("AF", color = if (m.focus == null) Amber else Color.White) }
                    Slider(
                        value = (m.focus ?: 0f) / r.minFocusDioptre,
                        onValueChange = { onChange(m.copy(focus = it * r.minFocusDioptre)) },
                        colors = sliderColors, modifier = Modifier.weight(1f),
                    )
                    Text("near", color = Muted, fontSize = 11.sp)
                }
                else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProCamera.wbModes.forEach { (mode, label) -> Choice(label, m.wb == mode) { onChange(m.copy(wb = mode)) } }
                }
            }
        }
    }
}

@Composable
private fun ProChip(label: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x55FFB347) else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, color = if (value == "Auto" || value == "AF") Color.White else Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LookStrip(looks: List<Look>, selected: Look, onSelect: (Look) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        items(looks, key = { it.key }) { l ->
            val isSel = l.key == selected.key
            val sub = when (l) {
                Look.None -> "No filter"
                is Look.Film -> FilmLooks.packLabel(l.film.pack)
                is Look.Tmpl -> "Template"
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
