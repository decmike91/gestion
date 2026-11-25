package com.decmike.gestion.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp
import com.decmike.gestion.data.DateFilter
import com.decmike.gestion.data.FileCategory
import com.decmike.gestion.data.FileItem
import com.decmike.gestion.data.GroupBy
import com.decmike.gestion.data.SizeFilter
import com.decmike.gestion.data.SortField
import kotlinx.coroutines.launch

fun hasStorageAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(app: GestionApp, onOpen: (FileItem) -> Unit, permissionTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files by app.files.files.collectAsStateWithLifecycle()
    val scanning by app.files.scanning.collectAsStateWithLifecycle()
    val loaded by app.files.loaded.collectAsStateWithLifecycle()
    val progress by app.files.progress.collectAsStateWithLifecycle()
    val error by app.files.lastError.collectAsStateWithLifecycle()
    val query by UiState.fileQuery.collectAsStateWithLifecycle()
    val audioState by app.audio.state.collectAsStateWithLifecycle()

    // Filtrage, tri et regroupement hors du thread principal (des dizaines de milliers de fichiers).
    var result by remember { mutableStateOf(emptyList<FileItem>()) }
    var grouped by remember { mutableStateOf(emptyList<Pair<String, List<FileItem>>>()) }
    androidx.compose.runtime.LaunchedEffect(files, query) {
        val (r, g) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val r = query.apply(files)
            r to groupFiles(r, query.group)
        }
        result = r
        grouped = g
    }
    val totalSize = remember(result) { result.sumOf { it.size } }
    val counts = remember(files) { files.groupingBy { it.category }.eachCount() }
    val sources = remember(files) { files.map { it.source }.distinct().sorted() }

    var menuFor by remember { mutableStateOf<FileItem?>(null) }
    var confirmDelete by remember { mutableStateOf<FileItem?>(null) }
    var addToPlaylist by remember { mutableStateOf<FileItem?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showGroup by remember { mutableStateOf(false) }
    val access = remember(permissionTick) { hasStorageAccess(context) }

    fun update(t: (com.decmike.gestion.data.FileQuery) -> com.decmike.gestion.data.FileQuery) {
        UiState.fileQuery.value = t(UiState.fileQuery.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fichiers") },
                actions = {
                    Box {
                        IconButton(onClick = { showGroup = true }) { Icon(Icons.Filled.ViewAgenda, "Regrouper") }
                        DropdownMenu(showGroup, { showGroup = false }) {
                            Text("Regrouper par", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
                            GroupBy.entries.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text((if (query.group == g) "✓ " else "") + g.label) },
                                    onClick = { update { it.copy(group = g) }; showGroup = false })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Trier") }
                        DropdownMenu(showSort, { showSort = false }) {
                            Text("Trier par", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
                            SortField.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text((if (query.sort == f) "✓ " else "") + f.label) },
                                    onClick = { update { it.copy(sort = f) }; showSort = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (query.descending) "Ordre décroissant" else "Ordre croissant") },
                                leadingIcon = { Icon(if (query.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null) },
                                onClick = { update { it.copy(descending = !it.descending) } })
                        }
                    }
                    IconButton(onClick = { app.files.rescanAsync() }, enabled = !scanning) {
                        Icon(Icons.Filled.Refresh, "Analyser")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!access) item {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Accès aux fichiers requis", style = MaterialTheme.typography.titleMedium)
                        Text("Pour lister vos téléchargements (PDF, vidéos, musiques…), autorisez « Accès à tous les fichiers ».")
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
                                try { context.startActivity(i) } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                            }
                        }) { Text("Autoriser") }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query.text,
                    onValueChange = { v -> update { it.copy(text = v) } },
                    placeholder = { Text("Rechercher un nom, un dossier…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.text.isNotEmpty()) IconButton(onClick = { update { it.copy(text = "") } }) { Icon(Icons.Filled.Clear, "Effacer") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FileCategory.entries.filter { (counts[it] ?: 0) > 0 }) { c ->
                        FilterChip(
                            selected = c in query.categories,
                            onClick = { update { q -> q.copy(categories = if (c in q.categories) q.categories - c else q.categories + c) } },
                            label = { Text("${c.label} ${counts[c] ?: 0}") },
                            leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp), tint = c.color()) }
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoiceChip(query.source ?: "Toutes sources", listOf<String?>(null) + sources, { it ?: "Toutes sources" }) { s ->
                        update { it.copy(source = s) }
                    }
                    ChoiceChip(query.size.label, SizeFilter.entries, { it.label }) { s -> update { it.copy(size = s) } }
                    ChoiceChip(query.date.label, DateFilter.entries, { it.label }) { d -> update { it.copy(date = d) } }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "${result.size} fichier(s) · ${formatSize(totalSize)}" + if (result.size != files.size) " (sur ${files.size})" else "",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (scanning) {
                        Text("Analyse en cours… $progress fichiers", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (files.isEmpty() && !scanning && loaded) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(64.dp))
                    Text("Aucun fichier analysé pour l'instant.")
                    Button(onClick = { app.files.rescanAsync() }) { Text("Analyser maintenant") }
                }
            }
            for ((header, list) in grouped) {
                if (header.isNotEmpty()) stickyHeader(key = "h_$header") {
                    Text(
                        "$header  (${list.size} · ${formatSize(list.sumOf { it.size })})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                items(list, key = { header + "|" + it.uri }) { f ->
                    Box {
                        FileRow(f, Modifier.combinedClickable(onClick = { onOpen(f) }, onLongClick = { menuFor = f }))
                        DropdownMenu(menuFor == f, { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Aperçu") }, onClick = { menuFor = null; onOpen(f) })
                            DropdownMenuItem(text = { Text("Ouvrir avec…") }, onClick = { menuFor = null; openWith(context, f) })
                            DropdownMenuItem(text = { Text("Partager") }, onClick = { menuFor = null; share(context, listOf(f)) })
                            if (f.category == FileCategory.AUDIO) {
                                DropdownMenuItem(text = { Text("Lire") }, onClick = {
                                    menuFor = null
                                    app.audio.tracks.value.firstOrNull { it.uri == f.uri }?.let { app.player.play(listOf(it)) }
                                })
                                DropdownMenuItem(text = { Text("Ajouter à une playlist") }, onClick = { menuFor = null; addToPlaylist = f })
                            }
                            DropdownMenuItem(text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) }, onClick = { menuFor = null; confirmDelete = f })
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Supprimer ?") },
            text = { Text("« ${f.name} » sera définitivement supprimé.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { app.files.delete(listOf(f)) }; confirmDelete = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler") } }
        )
    }
    addToPlaylist?.let { f ->
        PlaylistPickerDialog(app, audioState.playlists, onDismiss = { addToPlaylist = null }) { id ->
            app.audio.addToPlaylist(id, listOf(f.uri)); addToPlaylist = null
        }
    }
}

private fun groupFiles(result: List<FileItem>, group: GroupBy): List<Pair<String, List<FileItem>>> = when (group) {
    GroupBy.NONE -> listOf("" to result)
    GroupBy.CATEGORY -> result.groupBy { it.category.label }.toList()
    GroupBy.FOLDER -> result.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    GroupBy.MONTH -> result.groupBy { formatMonth(it.modified) }.toList()
    GroupBy.SOURCE -> result.groupBy { it.source }.toList()
    GroupBy.EXTENSION -> result.groupBy { it.extension.uppercase().ifEmpty { "SANS EXTENSION" } }.toList().sortedBy { it.first }
}

@Composable
fun FileRow(f: FileItem, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(f.category.color().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(f.category.icon(), null, tint = f.category.color()) }
        },
        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${formatSize(f.size)} · ${formatDate(f.modified)} · ${f.folder}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = trailing,
    )
}

@Composable
fun <T> ChoiceChip(label: String, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label, maxLines = 1) })
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun PlaylistPickerDialog(
    app: GestionApp,
    playlists: List<com.decmike.gestion.audio.Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter à une playlist") },
        text = {
            Column {
                playlists.forEach { p ->
                    TextButton(onClick = { onPick(p.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${p.name} (${p.tracks.size})", Modifier.fillMaxWidth())
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Nouvelle playlist") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = newName.isNotBlank(), onClick = { onPick(app.audio.createPlaylist(newName.trim()).id) }) { Text("Créer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
