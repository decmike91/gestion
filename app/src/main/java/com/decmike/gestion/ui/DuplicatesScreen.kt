package com.decmike.gestion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp
import com.decmike.gestion.data.DuplicateFinder
import com.decmike.gestion.data.DuplicateGroup
import com.decmike.gestion.data.DuplicateMode
import com.decmike.gestion.data.FileCategory
import com.decmike.gestion.data.FileItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(app: GestionApp, onOpen: (FileItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files by app.files.files.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(DuplicateMode.CONTENT) }
    var cats by remember { mutableStateOf(emptySet<FileCategory>()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val groups = UiState.duplicates
    val selected = UiState.dupSelection
    var confirm by remember { mutableStateOf(false) }

    fun selectAll(keepNewest: Boolean) {
        selected.clear()
        groups.forEach { g ->
            val keep = if (keepNewest) g.items.maxBy { it.modified } else g.items.minBy { it.modified }
            g.items.filter { it != keep }.forEach { selected += it.uri }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Doublons") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Méthode", style = MaterialTheme.typography.labelLarge)
                DuplicateMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = mode == m, onClick = { mode = m })
                        Column {
                            Text(m.label)
                            Text(m.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text("Types à analyser (aucun = tous)", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FileCategory.entries) { c ->
                        FilterChip(c in cats, { cats = if (c in cats) cats - c else cats + c }, label = { Text(c.label) })
                    }
                }
            }
            item {
                val p = progress
                if (p == null) {
                    Button(onClick = {
                        progress = 0f
                        job = scope.launch {
                            val src = if (cats.isEmpty()) files else files.filter { it.category in cats }
                            val res = DuplicateFinder.find(context, src, mode) { progress = it }
                            groups.clear(); groups.addAll(res)
                            selected.clear()
                            progress = null
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = files.isNotEmpty()) {
                        Text("Rechercher les doublons (${files.size} fichiers)")
                    }
                } else {
                    Column {
                        Text("Analyse… ${(p * 100).toInt()} %")
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { job?.cancel(); progress = null }) { Text("Annuler") }
                    }
                }
            }
            if (groups.isNotEmpty()) item {
                val wasted = groups.sumOf { it.wasted }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${groups.size} groupe(s) · ${formatSize(wasted)} récupérables", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selectAll(true) }) { Text("Garder le + récent") }
                            OutlinedButton(onClick = { selectAll(false) }) { Text("Garder le + ancien") }
                        }
                        if (selected.isNotEmpty()) {
                            val selSize = groups.flatMap { it.items }.filter { it.uri in selected }.sumOf { it.size }
                            Button(
                                onClick = { confirm = true },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Supprimer ${selected.size} fichier(s) · ${formatSize(selSize)}") }
                        }
                    }
                }
            } else if (progress == null) item {
                Text("Lancez une recherche pour afficher les doublons.", style = MaterialTheme.typography.bodyMedium)
            }
            items(groups, key = { it.key + it.items.first().uri }) { g -> GroupCard(g, selected, onOpen) }
        }
    }

    if (confirm) {
        val toDelete = groups.flatMap { it.items }.filter { it.uri in selected }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer ${toDelete.size} fichier(s) ?") },
            text = { Text("Les fichiers sélectionnés seront définitivement supprimés. Au moins un exemplaire est conservé dans chaque groupe que vous n'avez pas entièrement coché.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        app.files.delete(toDelete)
                        val gone = toDelete.map { it.uri }.toSet()
                        val updated = groups.map { DuplicateGroup(it.key, it.items.filterNot { f -> f.uri in gone }) }.filter { it.items.size > 1 }
                        groups.clear(); groups.addAll(updated)
                        selected.clear()
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun GroupCard(g: DuplicateGroup, selected: MutableList<String>, onOpen: (FileItem) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                "${g.items.size} exemplaires · ${formatSize(g.items.first().size)} · ${formatSize(g.wasted)} en trop",
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            g.items.forEach { f ->
                FileRow(f, Modifier.clickable { onOpen(f) }) {
                    Checkbox(f.uri in selected, { on -> if (on) selected += f.uri else selected -= f.uri })
                }
            }
        }
    }
}
