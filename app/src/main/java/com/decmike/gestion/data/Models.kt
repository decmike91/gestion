package com.decmike.gestion.data

import kotlinx.serialization.Serializable

enum class FileCategory(val label: String) {
    AUDIO("Audio"),
    VIDEO("Vidéo"),
    IMAGE("Image"),
    PDF("PDF"),
    DOCUMENT("Document"),
    ARCHIVE("Archive"),
    APK("Application"),
    OTHER("Autre");

    companion object {
        private val ext = mapOf(
            AUDIO to setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "aiff", "aif", "mid", "midi", "alac", "ape"),
            VIDEO to setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "wmv", "flv", "m4v", "ts", "mpeg", "mpg"),
            IMAGE to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "tif", "tiff", "avif"),
            PDF to setOf("pdf"),
            DOCUMENT to setOf(
                "txt", "md", "csv", "json", "xml", "log", "html", "htm", "rtf",
                "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "srt", "ini", "yml", "yaml"
            ),
            ARCHIVE to setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz"),
            APK to setOf("apk", "apks", "xapk")
        )

        fun of(name: String, mime: String?): FileCategory {
            val e = name.substringAfterLast('.', "").lowercase()
            ext.entries.firstOrNull { e in it.value }?.let { return it.key }
            val m = mime.orEmpty()
            return when {
                m.startsWith("audio/") -> AUDIO
                m.startsWith("video/") -> VIDEO
                m.startsWith("image/") -> IMAGE
                m == "application/pdf" -> PDF
                m.startsWith("text/") -> DOCUMENT
                else -> OTHER
            }
        }

        val TEXT_EXT = setOf("txt", "md", "csv", "json", "xml", "log", "html", "htm", "srt", "ini", "yml", "yaml", "rtf")
    }
}

enum class SourceType { LOCAL, DRIVE }

@Serializable
data class FileItem(
    val uri: String,
    val name: String,
    val displayPath: String,
    val folder: String,
    val source: String,
    val sourceType: SourceType,
    val size: Long,
    val modified: Long,
    val mime: String,
    val category: FileCategory,
    /** Chemin absolu (fichiers locaux uniquement). */
    val path: String? = null,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

@Serializable
data class DriveSource(
    val uri: String,
    val label: String,
    val provider: String,
    val addedAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
)

@Serializable
data class AppSettings(
    val localFolders: List<String> = DEFAULT_FOLDERS,
    val scanWholeStorage: Boolean = false,
    val includeHidden: Boolean = false,
    val drives: List<DriveSource> = emptyList(),
) {
    companion object {
        val DEFAULT_FOLDERS = listOf("Download", "Music", "Movies", "Documents", "Podcasts", "DCIM", "Pictures")
        val KNOWN_FOLDERS = listOf(
            "Download", "Music", "Movies", "Documents", "Podcasts", "Audiobooks",
            "DCIM", "Pictures", "Recordings", "Ringtones", "Notifications", "Alarms"
        )
    }
}

enum class SortField(val label: String) { NAME("Nom"), DATE("Date"), SIZE("Taille"), TYPE("Type"), FOLDER("Dossier") }
enum class GroupBy(val label: String) { NONE("Aucun"), CATEGORY("Type"), FOLDER("Dossier"), MONTH("Mois"), SOURCE("Source"), EXTENSION("Extension") }
enum class SizeFilter(val label: String, val min: Long, val max: Long) {
    ALL("Toutes tailles", 0, Long.MAX_VALUE),
    SMALL("< 1 Mo", 0, 1L shl 20),
    MEDIUM("1 – 100 Mo", 1L shl 20, 100L shl 20),
    LARGE("100 Mo – 1 Go", 100L shl 20, 1L shl 30),
    HUGE("> 1 Go", 1L shl 30, Long.MAX_VALUE),
}
enum class DateFilter(val label: String, val days: Int) {
    ALL("Toutes dates", 0), DAY("24 h", 1), WEEK("7 jours", 7), MONTH("30 jours", 30), YEAR("1 an", 365), OLDER("Plus d'un an", -365)
}
