package com.bigoke.grainlab.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.bigoke.grainlab.edit.LutBuilder
import com.bigoke.grainlab.io.MediaSaver
import java.io.File
import androidx.media3.transformer.Effects as Media3Effects

/**
 * Bakes the chosen look into a recorded clip: the raw recording is re-encoded with the same
 * colour lookup table the viewfinder used, then saved to Movies/GrainLab.
 * Must be started on the main thread.
 */
object VideoDeveloper {
    @OptIn(UnstableApi::class)
    fun develop(
        context: Context,
        input: File,
        lut: FloatArray?,
        onProgress: (Int) -> Unit,
        onDone: (Uri?) -> Unit,
    ) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())

        fun saveAsync(file: File, cleanup: List<File>) {
            Thread {
                val uri = runCatching { MediaSaver.saveVideo(app, file) }.getOrNull()
                cleanup.forEach { it.delete() }
                main.post { onDone(uri) }
            }.start()
        }

        if (lut == null) {
            saveAsync(input, listOf(input))
            return
        }

        val output = File(app.cacheDir, "developed_${System.currentTimeMillis()}.mp4")
        val effect = SingleColorLut.createFromCube(LutBuilder.toCube(lut))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(Media3Effects(emptyList(), listOf(effect)))
            .build()

        var finished = false
        val transformer = Transformer.Builder(app)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    saveAsync(output, listOf(input, output))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    // Keep the clip even if the look could not be applied.
                    saveAsync(input, listOf(input, output))
                }
            })
            .build()
        transformer.start(item, output.absolutePath)

        val holder = ProgressHolder()
        main.post(object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                main.postDelayed(this, 250)
            }
        })
    }
}
