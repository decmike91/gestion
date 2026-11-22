package com.decmike.gestion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(app: GestionApp, id: String, onBack: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val allTracks by app.audio.tracks.collectAsStateWithLifecycle()
    val ui by app.player.ui.collectAsStateWithLifecycle()
    val playlist = state.playlists.firstOrNull { it.id == id }
    var rename by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (playlist == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    val byUri = remember(allTracks) { allTracks.associateBy { it.uri } }
    val tracks = playlist.tracks.mapNotNull { byUri[it] }

    fun move(from: Int, to: Int) {
        if (to !in playlist.tracks.indices) return
        val l = playlist.tracks.toMutableList()
        l.add(to, l.removeAt(from))
        app.audio.updatePlaylist(playlist.copy(tracks = l))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "Renommer") }
                    IconButton(onClick = {
                        app.audio.updatePlaylist(playlist.copy(tracks = tracks.sortedBy { it.title.lowercase() }.map { it.uri }))
                    }) { Icon(Icons.Filled.SortByAlpha, "Trier A-Z") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer") }
                }
            )
        },
        bottomBar = { MiniPlayer(app) {} }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "${tracks.size} morceau(x) · ${formatDuration(tracks.sumOf { it.durationMs })}" +
                    if (tracks.size < playlist.tracks.size) " · ${playlist.tracks.size - tracks.size} introuvable(s)" else "",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { app.player.play(tracks) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, null); Text("Lire") }
                OutlinedButton(onClick = { app.player.play(tracks, tracks.indices.random(), true) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Filled.Shuffle, null); Text("Aléatoire")
                }
                OutlinedButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, null); Text("Ajouter") }
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { _, t -> t.uri }) { i, t ->
                    val realIndex = playlist.tracks.indexOf(t.uri)
                    TrackRow(t, ui.currentUri == t.uri, onClick = { app.player.play(tracks, i) }) {
                        Row {
                            IconButton(onClick = { move(realIndex, realIndex - 1) }, enabled = realIndex > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Monter") }
                            IconButton(onClick = { move(realIndex, realIndex + 1) }, enabled = realIndex < playlist.tracks.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Descendre")
                            }
                            IconButton(onClick = { app.audio.updatePlaylist(playlist.copy(tracks = playlist.tracks - t.uri)) }) {
                                Icon(Icons.Filled.Close, "Retirer")
                            }
                        }
                    }
                }
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Renommer") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { app.audio.updatePlaylist(playlist.copy(name = name.trim())); rename = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer la playlist ?") },
        text = { Text("Les fichiers audio ne sont pas supprimés. Les programmations liées le seront.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; app.audio.deletePlaylist(playlist.id); onBack() }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
    )
    if (adding) {
        var q by remember { mutableStateOf("") }
        val picked = remember { mutableStateListOf<String>() }
        val candidates = remember(q, allTracks) {
            val w = q.trim().lowercase()
            allTracks.filter { it.uri !in playlist.tracks && (w.isEmpty() || "${it.title} ${it.artist} ${it.album}".lowercase().contains(w)) }
                .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Ajouter des morceaux") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("Titre, chanteur…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { picked.clear(); picked.addAll(candidates.map { it.uri }) }) { Text("Tout sélectionner (${candidates.size})") }
                    Box(Modifier.heightIn(max = 400.dp)) {
                        LazyColumn {
                            items(candidates, key = { it.uri }) { t ->
                                ListItem(
                                    modifier = Modifier.clickable { if (t.uri in picked) picked.remove(t.uri) else picked.add(t.uri) },
                                    leadingContent = { Checkbox(t.uri in picked, null) },
                                    headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(t.artist, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { app.audio.addToPlaylist(playlist.id, picked.toList()); adding = false }) { Text("Ajouter ${picked.size}") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler") } }
        )
    }
}
