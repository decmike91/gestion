package com.decmike.gestion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp
import com.decmike.gestion.audio.Ambiance
import com.decmike.gestion.audio.Audiogram
import com.decmike.gestion.audio.DeviceKind
import com.decmike.gestion.audio.DeviceProfile
import com.decmike.gestion.audio.EqEngine
import com.decmike.gestion.audio.Ear
import com.decmike.gestion.audio.HearingTest
import com.decmike.gestion.audio.ListenerProfile
import com.decmike.gestion.ui.theme.LeftEarColor
import com.decmike.gestion.ui.theme.RightEarColor
import kotlin.math.roundToInt

// =====================================================================
// Écran principal « Son » : profil actif, auditeurs, systèmes, ambiances
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(
    app: GestionApp,
    onListener: (String) -> Unit,
    onDevice: (String) -> Unit,
    onAmbiance: (String) -> Unit,
) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val sound by app.audio.sound.collectAsStateWithLifecycle()
    val output by app.audio.output.collectAsStateWithLifecycle()
    val activeDevice = app.audio.activeDevice(state, output)
    var newListener by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profils audio") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Correction personnalisée", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(state.effectsEnabled, { on -> app.audio.update { it.copy(effectsEnabled = on) } })
                    }
                    Text("Sortie détectée : ${output.name} (${output.kind.label})", style = MaterialTheme.typography.bodySmall)
                    Text("Système appliqué : ${activeDevice?.name ?: "aucun"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    EqCurve(sound.left, sound.right)
                }
            }

            SectionTitle("Qui écoute ?")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.listeners, key = { it.id }) { l ->
                    FilterChip(
                        l.id == state.activeListenerId,
                        { app.audio.update { it.copy(activeListenerId = l.id) } },
                        label = { Text(l.name) },
                        leadingIcon = { Icon(Icons.Filled.Person, null) }
                    )
                }
                item { FilterChip(false, { newListener = true }, label = { Text("Ajouter") }, leadingIcon = { Icon(Icons.Filled.Add, null) }) }
            }
            state.listeners.forEach { l ->
                ListItem(
                    modifier = Modifier.clickable { onListener(l.id) },
                    leadingContent = { Icon(Icons.Filled.Hearing, null) },
                    headlineContent = { Text(l.name) },
                    supportingContent = { Text("Audiogramme : ${l.audiogramSource} · correction ${l.compensation} %") },
                    trailingContent = { Icon(Icons.Filled.Tune, "Régler") }
                )
            }

            SectionTitle("Ambiance")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(app.audio.ambiances, key = { it.id }) { a ->
                    FilterChip(a.id == state.activeAmbianceId, { app.audio.update { it.copy(activeAmbianceId = a.id) } }, label = { Text(a.name) })
                }
            }
            app.audio.ambiance(state.activeAmbianceId)?.let { a ->
                Text(a.description, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAmbiance("new") }) { Text("Créer une ambiance") }
                val active = app.audio.ambiance(state.activeAmbianceId)
                if (active != null && !active.builtIn) OutlinedButton(onClick = { onAmbiance(active.id) }) { Text("Modifier") }
            }

            SectionTitle("Systèmes d'écoute")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choix automatique selon la sortie", Modifier.weight(1f))
                Switch(state.autoDevice, { on -> app.audio.update { it.copy(autoDevice = on, manualDeviceId = it.manualDeviceId ?: activeDevice?.id) } })
            }
            state.devices.forEach { d ->
                ListItem(
                    modifier = Modifier.clickable { onDevice(d.id) },
                    leadingContent = { Icon(Icons.Filled.Headphones, null) },
                    headlineContent = {
                        Text(d.name, fontWeight = if (d.id == activeDevice?.id) FontWeight.Bold else FontWeight.Normal)
                    },
                    supportingContent = {
                        Text(d.kind.label + (d.matchName?.takeIf { it.isNotBlank() }?.let { " · associé à « $it »" } ?: ""))
                    },
                    trailingContent = {
                        if (!state.autoDevice) androidx.compose.material3.RadioButton(
                            state.manualDeviceId == d.id, { app.audio.update { it.copy(manualDeviceId = d.id) } }
                        )
                    }
                )
            }
            OutlinedButton(onClick = { onDevice("new") }) { Text("Ajouter un système d'écoute") }
            Spacer(Modifier.height(24.dp))
            Text(
                "La correction auditive est indicative et ne remplace pas un audioprothésiste. " +
                    "Un limiteur protège contre la saturation ; gardez un volume modéré.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (newListener) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newListener = false },
            title = { Text("Nouvel auditeur") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Prénom") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val l = ListenerProfile(name = name.trim())
                    app.audio.update { it.copy(listeners = it.listeners + l, activeListenerId = l.id) }
                    newListener = false
                    onListener(l.id)
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { newListener = false }) { Text("Annuler") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(title: String, onBack: () -> Unit, onDelete: (() -> Unit)?, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = { if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, unit: String = "", onChange: (Float) -> Unit) {
    Column {
        Text("$label : ${value.roundToInt()}$unit", style = MaterialTheme.typography.labelLarge)
        Slider(value, onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun BandSliders(gains: List<Float>, onChange: (List<Float>) -> Unit) {
    EqEngine.BAND_LABELS.forEachIndexed { i, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label Hz", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
            Slider(
                gains[i], { v -> onChange(gains.toMutableList().also { it[i] = (v * 2).roundToInt() / 2f }) },
                valueRange = -12f..12f, modifier = Modifier.weight(1f)
            )
            Text("%+.1f".format(gains[i]), Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
    TextButton(onClick = { onChange(List(gains.size) { 0f }) }) { Text("Remettre à plat") }
}

// =====================================================================
// Auditeur
// =====================================================================

@Composable
fun ListenerEditScreen(app: GestionApp, id: String, onBack: () -> Unit, onAudiogram: () -> Unit, onTest: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val l = state.listeners.firstOrNull { it.id == id }
    if (l == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    fun save(n: ListenerProfile) = app.audio.update { s -> s.copy(listeners = s.listeners.map { if (it.id == n.id) n else it }) }
    var confirm by remember { mutableStateOf(false) }

    EditorScaffold(l.name, onBack, if (state.listeners.size > 1) ({ confirm = true }) else null) {
        OutlinedTextField(l.name, { save(l.copy(name = it)) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionTitle("Audiogramme")
        AudiogramChart(l.audiogram)
        Text(hearingLevelLabel(l.audiogram), style = MaterialTheme.typography.bodySmall)
        Text("Source : ${l.audiogramSource}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAudiogram) { Text("Saisir / modifier") }
            Button(onClick = onTest) { Icon(Icons.Filled.Hearing, null); Spacer(Modifier.width(4.dp)); Text("Test auditif") }
        }
        SectionTitle("Personnalisation")
        LabeledSlider("Intensité de la correction auditive", l.compensation.toFloat(), 0f..100f, unit = " %") {
            save(l.copy(compensation = it.roundToInt()))
        }
        LabeledSlider("Graves (goût personnel)", l.bass.toFloat(), -8f..8f, steps = 15, unit = " dB") { save(l.copy(bass = it.roundToInt())) }
        LabeledSlider("Aigus (goût personnel)", l.treble.toFloat(), -8f..8f, steps = 15, unit = " dB") { save(l.copy(treble = it.roundToInt())) }
        LabeledSlider("Gain maximal autorisé", l.maxBoost.toFloat(), 0f..15f, steps = 14, unit = " dB") { save(l.copy(maxBoost = it.roundToInt())) }
        LabeledSlider("Balance (− gauche / + droite)", l.balance.toFloat(), -10f..10f, steps = 19, unit = " dB") { save(l.copy(balance = it.roundToInt())) }
        SectionTitle("Courbe de cet auditeur (sans système ni ambiance)")
        val s = EqEngine.compute(l, null, null, true)
        EqCurve(s.left, s.right)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Supprimer ${l.name} ?") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                app.audio.update { s ->
                    val rest = s.listeners.filterNot { it.id == l.id }
                    s.copy(listeners = rest, activeListenerId = if (s.activeListenerId == l.id) rest.firstOrNull()?.id else s.activeListenerId)
                }
                onBack()
            }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )
}

@Composable
fun AudiogramScreen(app: GestionApp, id: String, onBack: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val l = state.listeners.firstOrNull { it.id == id }
    if (l == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var a by remember { mutableStateOf(l.audiogram) }

    EditorScaffold("Audiogramme · ${l.name}", onBack, null) {
        Text(
            "Reportez les seuils de votre audiogramme (dB HL) ou partez d'un modèle. 0 = audition parfaite, plus le chiffre est grand, plus la perte est importante.",
            style = MaterialTheme.typography.bodySmall
        )
        ChoiceChip("Partir d'un modèle…", Audiogram.PRESETS, { it.first }) { a = it.second }
        AudiogramChart(a)
        Text(hearingLevelLabel(a), style = MaterialTheme.typography.bodySmall)
        for (ear in Ear.entries) {
            SectionTitle(ear.label)
            Audiogram.FREQS.forEachIndexed { i, f ->
                val values = if (ear == Ear.RIGHT) a.right else a.left
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f >= 1000) "${f / 1000} kHz" else "$f Hz", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        values[i].toFloat(),
                        { v ->
                            val nv = values.toMutableList().also { it[i] = (v / 5).roundToInt() * 5 }
                            a = if (ear == Ear.RIGHT) a.copy(right = nv) else a.copy(left = nv)
                        },
                        valueRange = -10f..100f, steps = 21, modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = if (ear == Ear.RIGHT) RightEarColor else LeftEarColor,
                            activeTrackColor = if (ear == Ear.RIGHT) RightEarColor else LeftEarColor,
                        )
                    )
                    Text("${values[i]} dB", Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        TextButton(onClick = { a = a.copy(left = a.right) }) { Text("Copier droite → gauche") }
        Button(onClick = {
            app.audio.update { s -> s.copy(listeners = s.listeners.map { if (it.id == l.id) it.copy(audiogram = a, audiogramSource = "Saisi manuellement") else it }) }
            onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
    }
}

@Composable
fun HearingTestScreen(app: GestionApp, id: String, onBack: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val l = state.listeners.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    val test = remember { HearingTest(scope) }
    val t by test.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        app.player.connect { if (it.isPlaying) it.pause() }
        onDispose { test.stop() }
    }
    if (l == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }

    EditorScaffold("Test auditif · ${l.name}", onBack, null) {
        when {
            t.finished -> {
                val result = HearingTest.toAudiogram(t)
                Text("Résultat", style = MaterialTheme.typography.titleLarge)
                AudiogramChart(result)
                Text(hearingLevelLabel(result), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Ce résultat est relatif (téléphone et casque non étalonnés). Il sert uniquement à personnaliser l'égalisation.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    app.audio.update { s ->
                        s.copy(listeners = s.listeners.map {
                            if (it.id == l.id) it.copy(audiogram = result, audiogramSource = "Test intégré (${output(app)})") else it
                        })
                    }
                    onBack()
                }, modifier = Modifier.fillMaxWidth()) { Text("Utiliser ce profil pour ${l.name}") }
                OutlinedButton(onClick = { test.start() }, modifier = Modifier.fillMaxWidth()) { Text("Recommencer") }
            }
            t.running -> {
                val freq = Audiogram.FREQS[t.freqIndex]
                Text("${t.ear.label} · ${if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"}", style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = { t.step / t.totalSteps.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text("Niveau : ${t.levelDb} dB", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { test.heard() },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                ) { Text("J'ENTENDS", style = MaterialTheme.typography.headlineMedium) }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { test.stop() }) { Text("Arrêter le test") }
            }
            else -> {
                Text("Préparation", style = MaterialTheme.typography.titleLarge)
                Text(
                    "1. Installez-vous au calme et mettez le casque ou les écouteurs que vous utiliserez.\n" +
                        "2. Réglez le volume du téléphone vers 50 % et ne le modifiez plus pendant le test.\n" +
                        "3. Des bips de plus en plus forts vont retentir dans une oreille puis l'autre.\n" +
                        "4. Appuyez sur « J'entends » dès que vous percevez un bip, même très faible.\n\n" +
                        "Durée : environ 3 à 5 minutes. Test indicatif, non médical."
                )
                Spacer(Modifier.height(16.dp))
                Text("Sortie actuelle : ${output(app)}", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { test.start() }, modifier = Modifier.fillMaxWidth()) { Text("Commencer") }
            }
        }
    }
}

private fun output(app: GestionApp) = app.audio.output.value.name

// =====================================================================
// Système d'écoute
// =====================================================================

@Composable
fun DeviceEditScreen(app: GestionApp, id: String, onBack: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val output by app.audio.output.collectAsStateWithLifecycle()
    val existing = state.devices.firstOrNull { it.id == id }
    var d by remember { mutableStateOf(existing ?: DeviceProfile(name = output.name, kind = output.kind)) }

    EditorScaffold(
        if (existing == null) "Nouveau système" else d.name, onBack,
        if (existing != null) ({
            app.audio.update { s -> s.copy(devices = s.devices.filterNot { it.id == d.id }) }; onBack()
        }) else null
    ) {
        OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ChoiceChip("Type : ${d.kind.label}", DeviceKind.entries, { it.label }) { d = d.copy(kind = it) }
        OutlinedTextField(
            d.matchName.orEmpty(), { d = d.copy(matchName = it.ifBlank { null }) },
            label = { Text("Associer à l'appareil nommé (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { d = d.copy(matchName = output.name, kind = output.kind) }) { Text("Associer à la sortie actuelle : ${output.name}") }
        Text(
            "Exemple : associez « Voiture » au nom Bluetooth de l'autoradio pour l'activer automatiquement.",
            style = MaterialTheme.typography.bodySmall
        )
        SectionTitle("Correction du système")
        EqCurve(d.gains.toFloatArray(), d.gains.toFloatArray())
        BandSliders(d.gains) { d = d.copy(gains = it) }
        Button(onClick = {
            app.audio.update { s ->
                s.copy(devices = if (s.devices.any { it.id == d.id }) s.devices.map { if (it.id == d.id) d else it } else s.devices + d)
            }
            onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
    }
}

// =====================================================================
// Ambiance personnalisée
// =====================================================================

@Composable
fun AmbianceEditScreen(app: GestionApp, id: String, onBack: () -> Unit) {
    val state by app.audio.state.collectAsStateWithLifecycle()
    val existing = state.customAmbiances.firstOrNull { it.id == id }
    var a by remember { mutableStateOf(existing ?: Ambiance(name = "Mon ambiance")) }

    EditorScaffold(
        if (existing == null) "Nouvelle ambiance" else a.name, onBack,
        if (existing != null) ({
            app.audio.update { s ->
                s.copy(
                    customAmbiances = s.customAmbiances.filterNot { it.id == a.id },
                    activeAmbianceId = if (s.activeAmbianceId == a.id) "neutre" else s.activeAmbianceId
                )
            }
            onBack()
        }) else null
    ) {
        OutlinedTextField(a.name, { a = a.copy(name = it) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(a.description, { a = a.copy(description = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ChoiceChip("Partir de…", app.audio.ambiances, { it.name }) { src ->
            a = a.copy(gains = src.gains, virtualizer = src.virtualizer, compression = src.compression)
        }
        SectionTitle("Égaliseur")
        EqCurve(a.gains.toFloatArray(), a.gains.toFloatArray())
        BandSliders(a.gains) { a = a.copy(gains = it) }
        LabeledSlider("Spatialisation", a.virtualizer / 10f, 0f..100f, unit = " %") { a = a.copy(virtualizer = (it * 10).roundToInt()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Compression douce (nuit, voix)", Modifier.weight(1f))
            Switch(a.compression, { a = a.copy(compression = it) })
        }
        Button(onClick = {
            app.audio.update { s ->
                s.copy(
                    customAmbiances = if (s.customAmbiances.any { it.id == a.id }) s.customAmbiances.map { if (it.id == a.id) a else it } else s.customAmbiances + a,
                    activeAmbianceId = a.id
                )
            }
            onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer et activer") }
    }
}
