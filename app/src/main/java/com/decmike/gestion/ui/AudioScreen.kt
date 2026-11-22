package com.decmike.gestion.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp
import com.decmike.gestion.audio.AudioState
import com.decmike.gestion.audio.Playlist
import com.decmike.gestion.audio.Schedule
import com.decmike.gestion.audio.Track
import com.decmike.gestion.schedule.ScheduleManager

private enum class TrackSort(val label: String) { TITLE("Titre"), ARTIST("Artiste"), ALBUM("Album"), RECENT("Récents"), DURATION("Durée") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(app: GestionApp, onOpenPlaylist: (String) -> Unit, onOpenSound: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Morceaux", "Artistes", "Albums", "Playlists", "Programmation")
    val tracks by app.audio.tracks.collectAsStateWithLifecycle()
    val state by app.audio.state.collectAsStateWithLifecycle()
    val search by UiState.audioSearch.collectAsStateWithLifecycle()

    val filtered = remember(tracks, search) {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) tracks
        else tracks.filter { t ->
            val hay = "${t.title} ${t.artist} ${t.album} ${t.genre} ${t.fileName}".lowercase()
            words.all { it in hay }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Musique") }) },
        bottomBar = { MiniPlayer(app, onOpenSound) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (tab < 3) OutlinedTextField(
                value = search,
                onValueChange = { UiState.audioSearch.value = it },
                placeholder = { Text("Titre, chanteur, album, genre…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { UiState.audioSearch.value = "" }) { Icon(Icons.Filled.Clear, null) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> TracksTab(app, filtered, state)
                1 -> GroupTab(app, filtered, state, Icons.Filled.Person) { it.artist }
                2 -> GroupTab(app, filtered, state, Icons.Filled.Album) { it.album }
                3 -> PlaylistsTab(app, state, onOpenPlaylist)
                4 -> SchedulesTab(app, state)
            }
        }
    }
}

@Composable
private fun TracksTab(app: GestionApp, tracks: List<Track>, state: AudioState) {
    var sort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    val sorted = remember(tracks, sort) {
        when (sort) {
            TrackSort.TITLE -> tracks.sortedBy { it.title.lowercase() }
            TrackSort.ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
            TrackSort.ALBUM -> tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
            TrackSort.RECENT -> tracks.sortedByDescending { it.modified }
            TrackSort.DURATION -> tracks.sortedByDescending { it.durationMs }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${sorted.size} morceaux", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            ChoiceChip("Tri : ${sort.label}", TrackSort.entries, { it.label }) { sort = it }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { app.player.play(sorted) }, enabled = sorted.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, null); Text("Tout lire") }
            OutlinedButton(onClick = { app.player.play(sorted, (sorted.indices).random(), shuffle = true) }, enabled = sorted.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, null); Text("Aléatoire")
            }
        }
        TrackList(app, sorted, state)
    }
}

@Composable
fun TrackList(app: GestionApp, tracks: List<Track>, state: AudioState, modifier: Modifier = Modifier) {
    val ui by app.player.ui.collectAsStateWithLifecycle()
    var pickFor by remember { mutableStateOf<Track?>(null) }
    LazyColumn(modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.uri }) { i, t ->
            TrackRow(t, playing = ui.currentUri == t.uri, onClick = { app.player.play(tracks, i) }) {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text("Lire ensuite / ajouter à la file") }, onClick = { open = false; app.player.enqueue(listOf(t)) })
                    DropdownMenuItem(text = { Text("Ajouter à une playlist") }, onClick = { open = false; pickFor = t })
                }
            }
        }
    }
    pickFor?.let { t ->
        PlaylistPickerDialog(app, state.playlists, onDismiss = { pickFor = null }) { id -> app.audio.addToPlaylist(id, listOf(t.uri)); pickFor = null }
    }
}

@Composable
fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (playing) Icons.Filled.PlayArrow else Icons.Filled.MusicNote, null,
                tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text("${t.artist} · ${t.album} · ${formatDuration(t.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailing,
    )
}

@Composable
private fun GroupTab(
    app: GestionApp, tracks: List<Track>, state: AudioState,
    icon: androidx.compose.ui.graphics.vector.ImageVector, key: (Track) -> String,
) {
    var selectedGroup by rememberSaveable(icon.name) { mutableStateOf<String?>(null) }
    val groups = remember(tracks) { tracks.groupBy(key).toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val sel = selectedGroup
    if (sel != null) {
        val list = groups[sel].orEmpty().sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
        Column {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedGroup = null }) { Text("← Retour") }
                Text(sel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { app.player.play(list) }) { Icon(Icons.Filled.PlayArrow, "Lire") }
                IconButton(onClick = { app.player.play(list, list.indices.random(), true) }) { Icon(Icons.Filled.Shuffle, "Aléatoire") }
            }
            TrackList(app, list, state)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups.entries.toList(), key = { it.key }) { (name, list) ->
                ListItem(
                    modifier = Modifier.clickable { selectedGroup = name },
                    leadingContent = { Icon(icon, null) },
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${list.size} morceau(x) · ${formatDuration(list.sumOf { it.durationMs })}") },
                    trailingContent = { IconButton(onClick = { app.player.play(list) }) { Icon(Icons.Filled.PlayArrow, "Lire") } }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(app: GestionApp, state: AudioState, onOpen: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { create = true }, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("Nouvelle playlist")
        }
        if (state.playlists.isEmpty()) Text(
            "Aucune playlist. Créez-en une puis ajoutez des morceaux depuis la liste.",
            Modifier.padding(16.dp)
        )
        LazyColumn {
            items(state.playlists, key = { it.id }) { p ->
                val tracks = app.audio.tracksOf(p)
                ListItem(
                    modifier = Modifier.clickable { onOpen(p.id) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${tracks.size} morceau(x) · ${formatDuration(tracks.sumOf { it.durationMs })}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { app.player.play(tracks) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, "Lire") }
                            IconButton(onClick = { app.player.play(tracks, tracks.indices.random(), true) }, enabled = tracks.isNotEmpty()) {
                                Icon(Icons.Filled.Shuffle, "Aléatoire")
                            }
                        }
                    }
                )
            }
        }
    }
    if (create) AlertDialog(
        onDismissRequest = { create = false },
        title = { Text("Nouvelle playlist") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val p = app.audio.createPlaylist(name.trim()); name = ""; create = false; onOpen(p.id)
            }) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = { create = false }) { Text("Annuler") } }
    )
}

private val DAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

fun describeDays(days: Set<Int>): String = when {
    days.isEmpty() -> "Une seule fois"
    days.size == 7 -> "Tous les jours"
    days == setOf(1, 2, 3, 4, 5) -> "En semaine"
    days == setOf(6, 7) -> "Le week-end"
    else -> days.sorted().joinToString(" ") { listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")[it - 1] }
}

@Composable
private fun SchedulesTab(app: GestionApp, state: AudioState) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun save(list: List<Schedule>) {
        app.audio.update { it.copy(schedules = list) }
        ScheduleManager.rescheduleAll(context)
    }
    Column(Modifier.fillMaxSize()) {
        if (!ScheduleManager.canExact(context)) Card(
            Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Pour un démarrage à l'heure exacte, autorisez les alarmes précises.")
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    )
                }) { Text("Autoriser") }
            }
        }
        Button(onClick = { creating = true }, enabled = state.playlists.isNotEmpty(), modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Alarm, null); Spacer(Modifier.width(6.dp)); Text("Programmer une playlist")
        }
        if (state.playlists.isEmpty()) Text("Créez d'abord une playlist.", Modifier.padding(horizontal = 16.dp))
        LazyColumn {
            items(state.schedules, key = { it.id }) { s ->
                val p = state.playlists.firstOrNull { it.id == s.playlistId }
                val amb = app.audio.ambiance(s.ambianceId)?.name
                val who = state.listeners.firstOrNull { it.id == s.listenerId }?.name
                ListItem(
                    modifier = Modifier.clickable { editing = s },
                    leadingContent = { Icon(Icons.Filled.Alarm, null) },
                    headlineContent = { Text("%02d:%02d · %s".format(s.hour, s.minute, p?.name ?: "?")) },
                    supportingContent = {
                        Text(listOfNotNull(
                            describeDays(s.days), who, amb, s.volume?.let { "vol. $it %" },
                            s.stopAfterMinutes?.let { "arrêt après $it min" }, if (s.shuffle) "aléatoire" else null
                        ).joinToString(" · "))
                    },
                    trailingContent = {
                        Switch(s.enabled, { on -> save(state.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) })
                    }
                )
            }
        }
    }
    if (creating || editing != null) {
        ScheduleDialog(
            app, state, editing,
            onDismiss = { creating = false; editing = null },
            onDelete = { id -> ScheduleManager.cancel(context, id); save(state.schedules.filterNot { it.id == id }); editing = null },
        ) { s ->
            save(if (state.schedules.any { it.id == s.id }) state.schedules.map { if (it.id == s.id) s else it } else state.schedules + s)
            creating = false; editing = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    app: GestionApp, state: AudioState, initial: Schedule?,
    onDismiss: () -> Unit, onDelete: (String) -> Unit, onSave: (Schedule) -> Unit,
) {
    val base = initial ?: Schedule(playlistId = state.playlists.first().id, hour = 7, minute = 30, listenerId = state.activeListenerId)
    val time = rememberTimePickerState(base.hour, base.minute, is24Hour = true)
    var playlistId by remember { mutableStateOf(base.playlistId) }
    var days by remember { mutableStateOf(base.days) }
    var shuffle by remember { mutableStateOf(base.shuffle) }
    var listenerId by remember { mutableStateOf(base.listenerId) }
    var ambianceId by remember { mutableStateOf(base.ambianceId) }
    var useVolume by remember { mutableStateOf(base.volume != null) }
    var volume by remember { mutableStateOf((base.volume ?: 40).toFloat()) }
    var stopAfter by remember { mutableStateOf(base.stopAfterMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Programmer" else "Modifier la programmation") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(time)
                ChoiceChip(
                    "Playlist : " + (state.playlists.firstOrNull { it.id == playlistId }?.name ?: "?"),
                    state.playlists, { it.name }) { playlistId = it.id }
                Text("Jours", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(7) { i ->
                        val d = i + 1
                        FilterChip(d in days, { days = if (d in days) days - d else days + d }, label = { Text(DAY_LABELS[i]) })
                    }
                }
                Text(describeDays(days), style = MaterialTheme.typography.bodySmall)
                ChoiceChip(
                    "Auditeur : " + (state.listeners.firstOrNull { it.id == listenerId }?.name ?: "inchangé"),
                    listOf<String?>(null) + state.listeners.map { it.id },
                    { id -> state.listeners.firstOrNull { it.id == id }?.name ?: "Inchangé" }) { listenerId = it }
                ChoiceChip(
                    "Ambiance : " + (app.audio.ambiance(ambianceId)?.name ?: "inchangée"),
                    listOf<String?>(null) + app.audio.ambiances.map { it.id },
                    { id -> app.audio.ambiance(id)?.name ?: "Inchangée" }) { ambianceId = it }
                ChoiceChip(
                    "Arrêt : " + (stopAfter?.let { "après $it min" } ?: "jamais"),
                    listOf<Int?>(null, 15, 30, 45, 60, 90, 120),
                    { it?.let { m -> "Après $m min" } ?: "Jamais" }) { stopAfter = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(shuffle, { shuffle = it }); Spacer(Modifier.width(8.dp)); Text("Lecture aléatoire")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(useVolume, { useVolume = it }); Spacer(Modifier.width(8.dp)); Text("Régler le volume : ${volume.toInt()} %")
                }
                if (useVolume) Slider(volume, { volume = it }, valueRange = 0f..100f)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    base.copy(
                        playlistId = playlistId, hour = time.hour, minute = time.minute, days = days, shuffle = shuffle,
                        listenerId = listenerId, ambianceId = ambianceId, volume = if (useVolume) volume.toInt() else null,
                        stopAfterMinutes = stopAfter, enabled = true,
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial.id) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

/** Mini-lecteur affiché en bas de l'onglet Musique. */
@Composable
fun MiniPlayer(app: GestionApp, onOpenSound: () -> Unit) {
    val ui by app.player.ui.collectAsStateWithLifecycle()
    val sound by app.audio.sound.collectAsStateWithLifecycle()
    val sleepAt by app.audio.sleepAt.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.player.connect()
        while (true) { app.player.refresh(); kotlinx.coroutines.delay(500) }
    }
    if (!ui.hasMedia) return
    Card(
        Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(ui.title.ifBlank { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(ui.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f,
                onValueChange = { app.player.seekTo((it * ui.durationMs).toLong()) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(ui.positionMs), style = MaterialTheme.typography.labelSmall)
                Text("${ui.index + 1}/${ui.count}", style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(ui.durationMs), style = MaterialTheme.typography.labelSmall)
            }
            PlayerControls(app, ui)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenSound, modifier = Modifier.weight(1f)) {
                    Text(
                        "🎚 " + (if (sound.enabled) sound.summary.ifBlank { "Profil audio" } else "Correction désactivée"),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                ChoiceChip(
                    if (sleepAt != null) "⏾ ${formatDuration((sleepAt!! - System.currentTimeMillis()).coerceAtLeast(0))}" else "⏾ Minuterie",
                    listOf<Int?>(null, 5, 15, 30, 45, 60, 90),
                    { it?.let { m -> "$m min" } ?: "Désactiver" }
                ) { m -> app.audio.sleepAt.value = m?.let { System.currentTimeMillis() + it * 60_000L } }
            }
        }
    }
}

@Composable
private fun PlayerControls(app: GestionApp, ui: com.decmike.gestion.audio.PlayerUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { app.player.setShuffle(!ui.shuffle) }) {
            Icon(Icons.Filled.Shuffle, "Aléatoire", tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { app.player.previous() }) { Icon(Icons.Filled.SkipPrevious, "Précédent") }
        FloatingActionButton(onClick = { app.player.toggle() }) {
            Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture/Pause")
        }
        IconButton(onClick = { app.player.next() }) { Icon(Icons.Filled.SkipNext, "Suivant") }
        IconButton(onClick = { app.player.cycleRepeat() }) {
            Icon(
                if (ui.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Répéter",
                tint = if (ui.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

