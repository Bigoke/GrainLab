package com.bigoke.grainlab

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.KeyEvent
import com.bigoke.grainlab.camera.CameraKeys
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bigoke.grainlab.data.CrashReporter
import com.bigoke.grainlab.data.Settings
import com.bigoke.grainlab.data.TemplateStore
import com.bigoke.grainlab.edit.FilmLooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.bigoke.grainlab.ui.BatchScreen
import com.bigoke.grainlab.ui.CameraScreen
import com.bigoke.grainlab.ui.EditorScreen
import com.bigoke.grainlab.ui.GalleryScreen
import com.bigoke.grainlab.ui.OnboardingScreen
import com.bigoke.grainlab.ui.GrainLabTheme
import com.bigoke.grainlab.ui.Ink
import com.bigoke.grainlab.ui.TemplatesScreen

sealed interface Screen {
    data object Camera : Screen
    data class Editor(val uri: Uri) : Screen
    data object Templates : Screen
    data object Batch : Screen
    data object Gallery : Screen
    data object Onboarding : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        lifecycleScope.launch { runCatching { com.bigoke.grainlab.gl.GlProcessor.warmUp() } }
        TemplateStore.init(this)
        Settings.init(this)
        FilmLooks.safeNames = Settings.storeSafeNames
        enableEdgeToEdge()
        val shared = sharedImage(intent)
        setContent {
            GrainLabTheme {
                App(shared)
            }
        }
    }

    // Volume buttons work as a shutter while the camera is on screen.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (CameraKeys.active && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event?.repeatCount == 0) CameraKeys.presses.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (CameraKeys.active && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun sharedImage(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_EDIT -> intent.data
            else -> null
        }
    }
}

@Composable
fun App(shared: Uri?) {
    var stack by remember {
        mutableStateOf(
            when {
                shared != null -> listOf<Screen>(Screen.Camera, Screen.Editor(shared))
                !Settings.onboarded -> listOf<Screen>(Screen.Camera, Screen.Onboarding)
                else -> listOf<Screen>(Screen.Camera)
            },
        )
    }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    // The camera stays upright; the editor and other screens can turn to landscape.
    val activity = LocalContext.current as? android.app.Activity
    val current = stack.last()
    LaunchedEffect(current) {
        activity?.requestedOrientation = if (current is Screen.Camera) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    crash?.let { report ->
        AlertDialog(
            onDismissRequest = { CrashReporter.clear(context); crash = null },
            title = { Text("GrainLab closed unexpectedly") },
            text = { Text("A crash report was saved on your phone. Share it so the problem can be fixed?") },
            confirmButton = {
                TextButton(onClick = { CrashReporter.share(context, report); CrashReporter.clear(context); crash = null }) { Text("Share report") }
            },
            dismissButton = { TextButton(onClick = { CrashReporter.clear(context); crash = null }) { Text("Not now") } },
        )
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        when (val s = stack.last()) {
            Screen.Camera -> CameraScreen(
                onOpenEditor = { push(Screen.Editor(it)) },
                onOpenTemplates = { push(Screen.Templates) },
                onOpenBatch = { push(Screen.Batch) },
                onOpenGallery = { push(Screen.Gallery) },
            )
            is Screen.Editor -> EditorScreen(uri = s.uri, onBack = { pop() })
            Screen.Templates -> TemplatesScreen(onBack = { pop() })
            Screen.Batch -> BatchScreen(onBack = { pop() })
            Screen.Gallery -> GalleryScreen(onBack = { pop() }, onOpenEditor = { push(Screen.Editor(it)) })
            Screen.Onboarding -> OnboardingScreen(onDone = { Settings.onboarded = true; pop() })
        }
    }
}
