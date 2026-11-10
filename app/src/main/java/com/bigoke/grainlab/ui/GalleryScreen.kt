package com.bigoke.grainlab.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigoke.grainlab.io.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A photo or video saved by GrainLab. */
data class GalleryItem(val uri: Uri, val video: Boolean, val date: Long)

private object Favourites {
    private const val KEY = "favourites"
    fun all(context: Context): Set<String> =
        context.getSharedPreferences("grainlab_gallery", Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun toggle(context: Context, uri: Uri) {
        val sp = context.getSharedPreferences("grainlab_gallery", Context.MODE_PRIVATE)
        val set = (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        if (!set.remove(uri.toString())) set.add(uri.toString())
        sp.edit().putStringSet(KEY, set).apply()
    }

    fun remove(context: Context, uris: Collection<Uri>) {
        val sp = context.getSharedPreferences("grainlab_gallery", Context.MODE_PRIVATE)
        val set = (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        set.removeAll(uris.map { it.toString() }.toSet())
        sp.edit().putStringSet(KEY, set).apply()
    }
}

private fun queryGallery(context: Context): List<GalleryItem> {
    val out = ArrayList<GalleryItem>()
    fun query(collection: Uri, video: Boolean) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val (sel, args) = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%GrainLab%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/GrainLab/%")
        }
        runCatching {
            context.contentResolver.query(collection, projection, sel, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    out.add(GalleryItem(ContentUris.withAppendedId(collection, c.getLong(idCol)), video, c.getLong(dateCol)))
                }
            }
        }
    }
    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    return out.sortedByDescending { it.date }
}

private fun thumbnail(context: Context, item: GalleryItem): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(item.uri, Size(360, 360), null)
    else if (!item.video) BitmapLoader.load(context, item.uri, 360)
    else null
}.getOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(onBack: () -> Unit, onOpenEditor: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 favourites, 2 photos, 3 videos
    var favs by remember { mutableStateOf(Favourites.all(context)) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) { items = withContext(Dispatchers.IO) { queryGallery(context) } }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            Favourites.remove(context, selected); selected = emptySet(); refresh++
        }
    }

    fun deleteSelected() {
        val uris = selected.toList()
        scope.launch {
            val failed = withContext(Dispatchers.IO) {
                uris.filter { u -> runCatching { context.contentResolver.delete(u, null, null) > 0 }.getOrDefault(false).not() }
            }
            if (failed.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                // Files saved before a reinstall need Android's own confirmation to delete.
                val pi = MediaStore.createDeleteRequest(context.contentResolver, failed)
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                Favourites.remove(context, uris)
                selected = emptySet()
                refresh++
                if (failed.isNotEmpty()) context.toast("Some items could not be deleted")
            }
        }
    }

    fun share() {
        val uris = ArrayList(selected)
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = if (items.firstOrNull { it.uri == uris[0] }?.video == true) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uris[0])
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share"))
    }

    val shown = items.filter {
        when (filter) {
            1 -> it.uri.toString() in favs
            2 -> !it.video
            3 -> it.video
            else -> true
        }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected.isNotEmpty()) selected = emptySet() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                if (selected.isEmpty()) "GrainLab gallery" else "${selected.size} selected",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) {
                IconButton(onClick = {
                    selected.forEach { Favourites.toggle(context, it) }
                    favs = Favourites.all(context)
                    selected = emptySet()
                }) { Icon(Icons.Filled.Star, "Favourite", tint = Amber) }
                IconButton(onClick = { share() }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("All", "Favourites", "Photos", "Videos").forEachIndexed { i, label -> Choice(label, filter == i) { filter = i } }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(if (items.isEmpty()) "Photos and videos you take or export in GrainLab appear here." else "Nothing here yet.", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown, key = { it.uri.toString() }) { item ->
                    var thumb by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(item.uri) { thumb = withContext(Dispatchers.IO) { thumbnail(context, item) } }
                    val isSel = item.uri in selected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .background(Color(0xFF2A2622))
                            .border(if (isSel) 3.dp else 0.dp, if (isSel) Amber else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (isSel) selected - item.uri else selected + item.uri
                                    } else if (item.video) {
                                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, "video/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        runCatching { context.startActivity(view) }
                                    } else onOpenEditor(item.uri)
                                },
                                onLongClick = { selected = selected + item.uri },
                            ),
                    ) {
                        thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        if (item.video) Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                        if (item.uri.toString() in favs) Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
                        if (isSel) Icon(Icons.Filled.CheckCircle, null, tint = Amber, modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} item${if (selected.size == 1) "" else "s"}?") },
            text = { Text("They will be removed from your phone. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; deleteSelected() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
