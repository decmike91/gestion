package com.decmike.gestion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class SettingsRepository(private val context: Context, private val store: JsonStore) {
    private val _settings = MutableStateFlow(store.load("settings.json", AppSettings()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        store.save("settings.json", s)
    }

    fun addDrive(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        val provider = FileScanner.providerName(uri)
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: provider
        val label = if (name == provider) provider else "$provider · $name"
        update { s ->
            if (s.drives.any { it.uri == uri.toString() }) s
            else s.copy(drives = s.drives + DriveSource(uri.toString(), label, provider))
        }
    }

    fun removeDrive(drive: DriveSource) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(drive.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        update { s -> s.copy(drives = s.drives.filterNot { it.uri == drive.uri }) }
    }
}

class FileRepository(
    private val context: Context,
    private val store: JsonStore,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val scanner = FileScanner(context)
    private val mutex = Mutex()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    /** Vrai une fois le cache disque chargé (lecture faite hors du thread principal). */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val cached = store.load<List<FileItem>>("files.json", emptyList())
            if (_files.value.isEmpty()) _files.value = cached
            _loaded.value = true
        }
    }

    /** Lance une analyse seulement si aucun fichier n'est connu. */
    fun rescanIfEmpty() {
        scope.launch {
            loaded.first { it }
            if (_files.value.isEmpty()) rescan()
        }
    }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun rescanAsync() {
        scope.launch { rescan() }
    }

    suspend fun rescan() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            _scanning.value = true
            _progress.value = 0
            _lastError.value = null
            val s = settingsRepo.settings.value
            val all = ArrayList<FileItem>()
            try {
                all += scanner.scanLocal(s) { _progress.value = it }
            } catch (e: Exception) {
                _lastError.value = "Stockage local : ${e.message}"
            }
            for (d in s.drives.filter { it.enabled }) {
                try {
                    val base = all.size
                    all += scanner.scanTree(d, s.includeHidden) { _progress.value = base + it }
                } catch (e: Exception) {
                    _lastError.value = "${d.label} : accès refusé ou indisponible"
                }
            }
            _files.value = all
            _loaded.value = true
            _progress.value = all.size
            store.save("files.json", all.toList())
        } finally {
            _scanning.value = false
            mutex.unlock()
        }
    }

    fun find(uri: String): FileItem? = _files.value.firstOrNull { it.uri == uri }

    /** Supprime définitivement un fichier (local ou drive). */
    suspend fun delete(items: Collection<FileItem>): Int = withContext(Dispatchers.IO) {
        var ok = 0
        val removed = HashSet<String>()
        for (item in items) {
            val success = try {
                if (item.sourceType == SourceType.LOCAL && item.path != null) File(item.path).delete()
                else DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri))
            } catch (_: Exception) {
                false
            }
            if (success) {
                ok++; removed += item.uri
            }
        }
        if (removed.isNotEmpty()) {
            _files.value = _files.value.filterNot { it.uri in removed }
            store.save("files.json", _files.value)
        }
        ok
    }

    /** Copie des documents choisis (ex. Google Drive) dans Download/Gestion. */
    suspend fun importDocuments(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Gestion")
        dest.mkdirs()
        var n = 0
        for (u in uris) {
            try {
                // Un fournisseur pourrait renvoyer un nom contenant « / » ou « .. » : on le neutralise.
                val name = (DocumentFile.fromSingleUri(context, u)?.name ?: "import_${System.currentTimeMillis()}")
                    .replace(Regex("[/\\\\:*?\"<>|\\x00]"), "_").trim('.', ' ').ifEmpty { "import_${System.currentTimeMillis()}" }
                var target = File(dest, name)
                var i = 1
                while (target.exists()) {
                    val ext = name.substringAfterLast('.', "")
                    val base = if (ext.isEmpty()) name else name.substringBeforeLast('.')
                    target = File(dest, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
                    i++
                }
                context.contentResolver.openInputStream(u)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                n++
            } catch (_: Exception) {
            }
        }
        if (n > 0) rescan()
        n
    }
}
