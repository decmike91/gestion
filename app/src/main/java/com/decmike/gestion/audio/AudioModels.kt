package com.decmike.gestion.audio

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Seuils auditifs en dB HL aux fréquences standard de l'audiogramme. */
@Serializable
data class Audiogram(
    val right: List<Int> = List(FREQS.size) { 0 },
    val left: List<Int> = List(FREQS.size) { 0 },
) {
    companion object {
        val FREQS = listOf(250, 500, 1000, 2000, 4000, 8000)

        val PRESETS: List<Pair<String, Audiogram>> = listOf(
            "Audition normale" to Audiogram(),
            "Presbyacousie légère" to Audiogram(listOf(10, 10, 15, 25, 35, 45), listOf(10, 10, 15, 25, 35, 45)),
            "Presbyacousie modérée" to Audiogram(listOf(20, 25, 30, 40, 55, 65), listOf(20, 25, 30, 40, 55, 65)),
            "Perte aux aigus (bruit)" to Audiogram(listOf(5, 10, 10, 20, 50, 30), listOf(5, 10, 10, 20, 50, 30)),
            "Perte aux graves" to Audiogram(listOf(40, 35, 25, 15, 10, 10), listOf(40, 35, 25, 15, 10, 10)),
        )
    }
}

@Serializable
data class ListenerProfile(
    val id: String = newId(),
    val name: String,
    val audiogram: Audiogram = Audiogram(),
    /** Part de correction appliquée (0–100 %). */
    val compensation: Int = 60,
    /** Préférences personnelles en dB. */
    val bass: Int = 0,
    val treble: Int = 0,
    /** Gain maximal autorisé (protection de l'audition). */
    val maxBoost: Int = 12,
    /** Balance en dB : négatif = vers la gauche, positif = vers la droite. */
    val balance: Int = 0,
    val audiogramSource: String = "Aucun",
)

enum class DeviceKind(val label: String) {
    SPEAKER("Haut-parleur du téléphone"),
    WIRED("Écouteurs / casque filaire"),
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    CAR("Voiture"),
    OTHER("Autre"),
}

@Serializable
data class DeviceProfile(
    val id: String = newId(),
    val name: String,
    val kind: DeviceKind,
    /** Correction propre au système d'écoute, une valeur par bande (dB). */
    val gains: List<Float> = List(EqEngine.BANDS.size) { 0f },
    /** Nom de l'appareil Bluetooth/USB auquel associer automatiquement ce profil. */
    val matchName: String? = null,
)

@Serializable
data class Ambiance(
    val id: String = newId(),
    val name: String,
    val description: String = "",
    val gains: List<Float> = List(EqEngine.BANDS.size) { 0f },
    /** Spatialisation 0–1000. */
    val virtualizer: Int = 0,
    /** Compression douce (mode nuit / voix). */
    val compression: Boolean = false,
    val builtIn: Boolean = false,
)

@Serializable
data class Playlist(
    val id: String = newId(),
    val name: String,
    val tracks: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
)

@Serializable
data class Schedule(
    val id: String = newId(),
    val playlistId: String,
    val hour: Int,
    val minute: Int,
    /** Jours 1 = lundi … 7 = dimanche. Vide = une seule fois. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    val shuffle: Boolean = false,
    val listenerId: String? = null,
    val ambianceId: String? = null,
    /** Volume média 0–100, null = inchangé. */
    val volume: Int? = null,
    /** Arrêt automatique après N minutes, null = jamais. */
    val stopAfterMinutes: Int? = null,
)

@Serializable
data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileName: String,
    val folder: String,
    val modified: Long = 0,
    val genre: String = "",
    val year: Int = 0,
)

@Serializable
data class AudioState(
    val listeners: List<ListenerProfile> = listOf(ListenerProfile(name = "Moi")),
    val devices: List<DeviceProfile> = DEFAULT_DEVICES,
    val customAmbiances: List<Ambiance> = emptyList(),
    val activeListenerId: String? = null,
    val activeAmbianceId: String = "neutre",
    val autoDevice: Boolean = true,
    val manualDeviceId: String? = null,
    val effectsEnabled: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
) {
    companion object {
        private fun g(vararg v: Number) = v.map { it.toFloat() }

        val DEFAULT_DEVICES = listOf(
            DeviceProfile(name = "Haut-parleur du téléphone", kind = DeviceKind.SPEAKER, gains = g(-12, -8, -2, 2, 1, 0, 0, -1, 0, 0)),
            DeviceProfile(name = "Écouteurs filaires", kind = DeviceKind.WIRED),
            DeviceProfile(name = "Casque / écouteurs Bluetooth", kind = DeviceKind.BLUETOOTH),
            DeviceProfile(name = "Voiture", kind = DeviceKind.CAR, gains = g(0, 0, -1, -2, 0, 1, 2, 2, 1, 0)),
            DeviceProfile(name = "Sortie USB", kind = DeviceKind.USB),
        )

        val BUILT_IN_AMBIANCES = listOf(
            Ambiance("neutre", "Neutre", "Aucune coloration", builtIn = true),
            Ambiance("concert", "Concert", "Live, ample et spatialisé", g(4, 3, 1, 0, -1, 0, 1, 2, 3, 2), virtualizer = 800, builtIn = true),
            Ambiance("club", "Club", "Basses profondes, aigus brillants", g(7, 6, 3, 0, -2, -1, 0, 2, 3, 3), virtualizer = 300, builtIn = true),
            Ambiance("salon", "Salon cosy", "Chaleureux et doux", g(2, 2, 2, 1, 0, 0, -1, -2, -2, -3), virtualizer = 200, builtIn = true),
            Ambiance("voix", "Voix / Podcast", "Intelligibilité des paroles", g(-6, -4, -2, 0, 2, 3, 4, 3, 0, -2), compression = true, builtIn = true),
            Ambiance("classique", "Classique", "Naturel, dynamique respectée", g(0, 0, 0, 0, 0, 0, -1, -1, 1, 2), virtualizer = 400, builtIn = true),
            Ambiance("jazz", "Jazz", "Médiums ronds, cuivres présents", g(2, 2, 1, 2, -1, -1, 0, 1, 2, 2), builtIn = true),
            Ambiance("rock", "Rock", "Énergie dans les graves et les aigus", g(5, 4, 2, -1, -2, -1, 1, 3, 4, 4), builtIn = true),
            Ambiance("electro", "Électro", "Sub-basses et brillance", g(6, 5, 2, 0, -1, 1, 0, 1, 4, 5), virtualizer = 500, builtIn = true),
            Ambiance("nuit", "Nuit", "Volume homogène, pas de pics", g(-2, -1, 0, 0, 1, 1, 1, 0, -1, -2), compression = true, builtIn = true),
            Ambiance("relax", "Détente", "Doux, aigus atténués", g(1, 1, 1, 0, 0, -1, -2, -3, -4, -5), virtualizer = 300, builtIn = true),
        )
    }
}
