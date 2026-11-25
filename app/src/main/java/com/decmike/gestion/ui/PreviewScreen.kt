package com.decmike.gestion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.decmike.gestion.GestionApp
import com.decmike.gestion.data.FileCategory
import com.decmike.gestion.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(app: GestionApp, uri: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = remember(uri) { app.files.find(uri) }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Aperçu", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { share(context, listOf(item)) }) { Icon(Icons.Filled.Share, "Partager") }
                        IconButton(onClick = { openWith(context, item) }) { Icon(Icons.Filled.OpenInNew, "Ouvrir avec") }
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Supprimer") }
                    }
                }
            )
        }
    ) { pad ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Fichier introuvable") }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    item.category == FileCategory.IMAGE -> ImagePreview(item)
                    item.category == FileCategory.PDF -> PdfPreview(item)
                    item.category == FileCategory.VIDEO -> VideoPreview(item)
                    item.category == FileCategory.AUDIO -> AudioPreview(app, item)
                    item.extension in FileCategory.TEXT_EXT -> TextPreview(item)
                    else -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(item.category.icon(), null, Modifier.size(96.dp), tint = item.category.color())
                        Text("Pas d'aperçu intégré pour ce type de fichier.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openWith(context, item) }) { Text("Ouvrir avec une autre application") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    InfoLine("Taille", formatSize(item.size))
                    InfoLine("Modifié", formatDate(item.modified))
                    InfoLine("Type", "${item.category.label} · ${item.mime.ifBlank { item.extension }}")
                    InfoLine("Emplacement", item.displayPath)
                    InfoLine("Source", item.source)
                }
            }
        }
    }
    if (confirm && item != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer ?") },
            text = { Text("« ${item.name} » sera définitivement supprimé.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { if (app.files.delete(listOf(item)) > 0) onBack() }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label : ", style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun openStream(context: Context, item: FileItem) =
    if (item.path != null) File(item.path).inputStream() else context.contentResolver.openInputStream(Uri.parse(item.uri))

private fun decodeSampled(context: Context, item: FileItem, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(context, item)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return openStream(context, item)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun ImagePreview(item: FileItem) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { try { decodeSampled(context, item, 2048) } catch (_: Exception) { null } }
    }
    val b = bmp
    if (b == null) Loading()
    else Image(b.asImageBitmap(), item.name, Modifier.fillMaxSize().background(Color.Black), contentScale = ContentScale.Fit)
}

@Composable
private fun PdfPreview(item: FileItem) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val renderer = remember(item.uri) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(item.uri), "r")
            pfd?.let { PdfRenderer(it) }
        } catch (e: Exception) {
            error = "PDF illisible ou protégé"; null
        }
    }
    DisposableEffect(renderer) { onDispose { renderer?.let { r -> synchronized(r) { try { r.close() } catch (_: Exception) {} } } } }
    LaunchedEffect(renderer, page) {
        val r = renderer ?: return@LaunchedEffect
        error = null
        pageCount = try { r.pageCount } catch (_: Exception) { 0 }
        bitmap = withContext(Dispatchers.IO) {
            // Page corrompue ou rendu interrompu par la fermeture : pas de plantage.
            try { synchronized(r) {
                r.openPage(page).use { p ->
                    // Largeur cible ~1600 px, sans dépasser ~24 Mpx (affiches, plans…).
                    val scale = (1600f / p.width).coerceIn(0.1f, 3f)
                        .coerceAtMost(kotlin.math.sqrt(24_000_000f / (p.width.toFloat() * p.height)))
                    val w = (p.width * scale).toInt().coerceAtLeast(1)
                    val h = (p.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } } catch (e: Exception) {
                null
            }
        }
        if (bitmap == null) error = "Impossible d'afficher la page ${page + 1}"
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            val b = bitmap
            when {
                error != null -> Text(error!!, Modifier.align(Alignment.Center))
                b == null -> Loading()
                else -> Image(
                    b.asImageBitmap(), "Page ${page + 1}",
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentScale = ContentScale.FillWidth
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.Filled.ChevronLeft, "Précédente") }
            Text("Page ${page + 1} / $pageCount")
            IconButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Icon(Icons.Filled.ChevronRight, "Suivante") }
        }
    }
}

@Composable
private fun VideoPreview(item: FileItem) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}

@Composable
private fun AudioPreview(app: GestionApp, item: FileItem) {
    val context = LocalContext.current
    val tracks by app.audio.tracks.collectAsStateWithLifecycle()
    val track = tracks.firstOrNull { it.uri == item.uri }
    val art by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                if (item.path != null) r.setDataSource(item.path) else r.setDataSource(context, Uri.parse(item.uri))
                r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (_: Exception) { null } finally { try { r.release() } catch (_: Exception) {} }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        val a = art
        if (a != null) Image(a.asImageBitmap(), null, Modifier.fillMaxWidth(0.7f).aspectRatio(1f), contentScale = ContentScale.Crop)
        else Icon(FileCategory.AUDIO.icon(), null, Modifier.size(120.dp), tint = FileCategory.AUDIO.color())
        Spacer(Modifier.height(16.dp))
        Text(track?.title ?: item.name, style = MaterialTheme.typography.headlineSmall)
        Text(track?.artist ?: "", style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(track?.album, track?.durationMs?.let { formatDuration(it) }).joinToString(" · "))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { track?.let { app.player.play(listOf(it)) } }, enabled = track != null) {
            Icon(Icons.Filled.PlayArrow, null); Text("Lire avec le lecteur")
        }
    }
}

@Composable
private fun TextPreview(item: FileItem) {
    val context = LocalContext.current
    val text by produceState<String?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                openStream(context, item)?.use { s ->
                    val buf = ByteArray(128 * 1024)
                    val n = s.read(buf).coerceAtLeast(0)
                    String(buf, 0, n, Charsets.UTF_8) + if (item.size > n) "\n\n… (aperçu limité aux 128 premiers Ko)" else ""
                }
            } catch (e: Exception) { "Lecture impossible : ${e.message}" }
        }
    }
    val t = text
    if (t == null) Loading()
    else SelectionContainer {
        Text(
            t, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
        )
    }
}
