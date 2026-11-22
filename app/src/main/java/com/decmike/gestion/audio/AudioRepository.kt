package com.decmike.gestion.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.decmike.gestion.data.FileCategory
import com.decmike.gestion.data.FileItem
import com.decmike.gestion.data.JsonStore
import com.decmike.gestion.data.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OutputInfo(val kind: DeviceKind, val name: String)

class AudioRepository(
    private val context: Context,
    private val store: JsonStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(store.load("audio.json", AudioState()).let { s ->
        if (s.activeListenerId == null) s.copy(activeListenerId = s.listeners.firstOrNull()?.id) else s
    })
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow(store.load<List<Track>>("tracks.json", emptyList()))
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _output = MutableStateFlow(OutputInfo(DeviceKind.SPEAKER, "Haut-parleur"))
    val output: StateFlow<OutputInfo> = _output.asStateFlow()

    /** Heure (epoch ms) d'arrêt programmé de la lecture. */
    val sleepAt = MutableStateFlow<Long?>(null)

    val ambiances: List<Ambiance> get() = AudioState.BUILT_IN_AMBIANCES + _state.value.customAmbiances

    fun update(transform: (AudioState) -> AudioState) {
        val s = transform(_state.value)
        _state.value = s
        scope.launch(Dispatchers.IO) { store.save("audio.json", s) }
    }

    // ---------- Résolution du profil actif ----------

    fun activeDevice(s: AudioState = _state.value, out: OutputInfo = _output.value): DeviceProfile? {
        if (!s.autoDevice) s.devices.firstOrNull { it.id == s.manualDeviceId }?.let { return it }
        s.devices.firstOrNull { !it.matchName.isNullOrBlank() && out.name.contains(it.matchName, ignoreCase = true) }
            ?.let { return it }
        return s.devices.firstOrNull { it.kind == out.kind && it.matchName.isNullOrBlank() }
            ?: s.devices.firstOrNull { it.kind == out.kind }
    }

    fun activeListener(s: AudioState = _state.value) = s.listeners.firstOrNull { it.id == s.activeListenerId }

    fun ambiance(id: String?) = ambiances.firstOrNull { it.id == id }

    val sound: StateFlow<SoundSettings> = combine(_state, _output) { s, out ->
        EqEngine.compute(activeListener(s), activeDevice(s, out), ambiance(s.activeAmbianceId), s.effectsEnabled)
    }.distinctUntilChanged().stateIn(
        scope, SharingStarted.Eagerly,
        EqEngine.compute(null, null, null, false)
    )

    // ---------- Détection du système d'écoute ----------

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshOutput()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshOutput()
        }, Handler(Looper.getMainLooper()))
        refreshOutput()
    }

    fun refreshOutput() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun pick(vararg types: Int) = devices.firstOrNull { it.type in types }
        val bleTypes = intArrayOf(26, 27) // TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER (API 31+)
        val info = pick(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, *bleTypes)?.let {
            OutputInfo(DeviceKind.BLUETOOTH, it.productName?.toString().orEmpty().ifBlank { "Bluetooth" })
        } ?: pick(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)?.let {
            OutputInfo(DeviceKind.USB, it.productName?.toString().orEmpty().ifBlank { "USB" })
        } ?: pick(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)?.let {
            OutputInfo(DeviceKind.WIRED, "Prise casque")
        } ?: OutputInfo(DeviceKind.SPEAKER, "Haut-parleur")
        _output.value = info
    }

    fun setVolumePercent(pct: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct.coerceIn(0, 100) / 100f).toInt(), 0)
    }

    // ---------- Bibliothèque musicale ----------

    suspend fun refreshLibrary(files: List<FileItem>) = withContext(Dispatchers.IO) {
        val audio = files.filter { it.category == FileCategory.AUDIO }
        val previous = _tracks.value.associateBy { it.uri }
        val index = mediaStoreIndex()
        val result = audio.map { f ->
            previous[f.uri]?.takeIf { it.modified == f.modified }
                ?: f.path?.let { p -> index[p] }?.copy(uri = f.uri, fileName = f.name, folder = f.folder, modified = f.modified)
                ?: retrieve(f)
        }
        _tracks.value = result
        store.save("tracks.json", result)
    }

    private fun mediaStoreIndex(): Map<String, Track> {
        val map = HashMap<String, Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR,
        )
        try {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    val name = path.substringAfterLast('/')
                    map[path] = Track(
                        uri = "",
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: titleFromName(name),
                        artist = cleanArtist(c.getString(2), name),
                        album = c.getString(3)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Album inconnu",
                        durationMs = c.getLong(4),
                        fileName = name,
                        folder = "",
                        year = if (c.isNull(5)) 0 else c.getInt(5),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun retrieve(f: FileItem): Track {
        val r = MediaMetadataRetriever()
        return try {
            if (f.sourceType == SourceType.LOCAL && f.path != null) r.setDataSource(f.path)
            else r.setDataSource(context, Uri.parse(f.uri))
            Track(
                uri = f.uri,
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: titleFromName(f.name),
                artist = cleanArtist(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), f.name
                ),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Album inconnu",
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                fileName = f.name,
                folder = f.folder,
                modified = f.modified,
                genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty(),
                year = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0,
            )
        } catch (_: Exception) {
            Track(f.uri, titleFromName(f.name), cleanArtist(null, f.name), "Album inconnu", 0, f.name, f.folder, f.modified)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun titleFromName(name: String): String {
        val base = name.substringBeforeLast('.')
        return if (" - " in base) base.substringAfter(" - ").trim() else base
    }

    private fun cleanArtist(raw: String?, name: String): String {
        if (!raw.isNullOrBlank() && raw != "<unknown>") return raw
        val base = name.substringBeforeLast('.')
        return if (" - " in base) base.substringBefore(" - ").trim() else "Artiste inconnu"
    }

    // ---------- Playlists ----------

    fun createPlaylist(name: String, tracks: List<String> = emptyList()): Playlist {
        val p = Playlist(name = name, tracks = tracks)
        update { it.copy(playlists = it.playlists + p) }
        return p
    }

    fun updatePlaylist(p: Playlist) = update { s -> s.copy(playlists = s.playlists.map { if (it.id == p.id) p else it }) }

    fun deletePlaylist(id: String) = update { s ->
        s.copy(playlists = s.playlists.filterNot { it.id == id }, schedules = s.schedules.filterNot { it.playlistId == id })
    }

    fun addToPlaylist(id: String, uris: List<String>) = update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(tracks = it.tracks + uris.filterNot { u -> u in it.tracks }) else it })
    }

    fun tracksOf(p: Playlist): List<Track> {
        val byUri = _tracks.value.associateBy { it.uri }
        return p.tracks.mapNotNull { byUri[it] }
    }
}
