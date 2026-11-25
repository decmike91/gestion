package com.decmike.gestion.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decmike.gestion.GestionApp
import com.decmike.gestion.data.AppSettings
import com.decmike.gestion.schedule.ScheduleManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: GestionApp, permissionTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val files by app.files.files.collectAsStateWithLifecycle()

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            app.settings.addDrive(uri)
            app.files.rescanAsync()
        }
    }
    val pickDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val n = app.files.importDocuments(uris)
            Toast.makeText(context, "$n fichier(s) importé(s) dans Download/Gestion", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sources & réglages") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Drives et dossiers connectés")
            Text(
                "Ajoutez un dossier d'un drive (OneDrive, Dropbox, Nextcloud, Box, pCloud…), d'une clé USB, d'une carte SD ou d'un partage réseau. " +
                    "L'application du drive doit être installée et connectée à votre compte : elle apparaît alors dans le sélecteur Android.",
                style = MaterialTheme.typography.bodySmall
            )
            settings.drives.forEach { d ->
                val count = files.count { it.source == d.label }
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Cloud, null) },
                    headlineContent = { Text(d.label) },
                    supportingContent = { Text("$count fichier(s) · ${if (d.enabled) "inclus dans l'analyse" else "désactivé"}") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(d.enabled, { on ->
                                app.settings.update { s -> s.copy(drives = s.drives.map { if (it.uri == d.uri) it.copy(enabled = on) else it }) }
                            })
                            IconButton(onClick = { app.settings.removeDrive(d); app.files.rescanAsync() }) { Icon(Icons.Filled.Delete, "Retirer") }
                        }
                    }
                )
            }
            Button(onClick = { pickTree.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddToDrive, null); Spacer(Modifier.width(8.dp)); Text("Connecter un drive / dossier")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickDocs.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Importer des fichiers (Google Drive…)")
            }
            Text(
                "Google Drive ne permet pas de connecter un dossier entier depuis Android : utilisez « Importer » pour copier les fichiers voulus sur le téléphone, " +
                    "ou rendez-les disponibles hors connexion.",
                style = MaterialTheme.typography.bodySmall
            )

            SectionTitle("Stockage du téléphone")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Analyser tout le stockage", Modifier.weight(1f))
                Switch(settings.scanWholeStorage, { on -> app.settings.update { it.copy(scanWholeStorage = on) } })
            }
            if (!settings.scanWholeStorage) {
                Text("Dossiers analysés :", style = MaterialTheme.typography.labelLarge)
                AppSettings.KNOWN_FOLDERS.chunked(2).forEach { row ->
                    Row {
                        row.forEach { f ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(f in settings.localFolders, { on ->
                                    app.settings.update { s -> s.copy(localFolders = if (on) s.localFolders + f else s.localFolders - f) }
                                })
                                Text(f)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inclure les fichiers cachés", Modifier.weight(1f))
                Switch(settings.includeHidden, { on -> app.settings.update { it.copy(includeHidden = on) } })
            }
            Button(onClick = { app.files.rescanAsync() }, modifier = Modifier.fillMaxWidth()) { Text("Analyser maintenant (${files.size} fichiers connus)") }

            SectionTitle("Autorisations")
            val storageOk = remember(permissionTick) { hasStorageAccess(context) }
            PermissionRow("Accès à tous les fichiers", storageOk) {
                context.startActivity(
                    Intent(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            }
            PermissionRow("Alarmes précises (programmation)", remember(permissionTick) { ScheduleManager.canExact(context) }) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                )
            }
            SectionTitle("À propos")
            Text("Gestion 1.0 — gestionnaire de fichiers et lecteur audio personnalisé.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(if (granted) "Accordée" else "Non accordée", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (!granted) OutlinedButton(onClick = onRequest) { Text("Autoriser") }
    }
}
