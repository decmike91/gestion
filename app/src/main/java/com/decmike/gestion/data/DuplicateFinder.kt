package com.decmike.gestion.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class DuplicateMode(val label: String, val description: String) {
    CONTENT("Contenu identique", "Compare taille puis empreinte SHA-256 : doublons certains."),
    NAME_SIZE("Même nom et taille", "Rapide, sans lecture des fichiers."),
    SIMILAR_NAME("Nom similaire", "Ignore « (1) », « copie », « - Copy », la casse… À vérifier."),
}

data class DuplicateGroup(val key: String, val items: List<FileItem>) {
    val wasted: Long get() = items.sortedBy { it.size }.dropLast(1).sumOf { it.size }
}

object DuplicateFinder {

    suspend fun find(
        context: Context,
        files: List<FileItem>,
        mode: DuplicateMode,
        onProgress: (Float) -> Unit,
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        when (mode) {
            DuplicateMode.NAME_SIZE -> files.groupBy { it.name.lowercase() + "|" + it.size }
                .filter { it.value.size > 1 }
                .map { DuplicateGroup(it.key, it.value) }

            DuplicateMode.SIMILAR_NAME -> files.groupBy { it.category.name + "|" + normalizeName(it.name) }
                .filter { it.value.size > 1 && it.key.substringAfter('|').length >= 2 }
                .map { DuplicateGroup(it.key, it.value) }

            DuplicateMode.CONTENT -> {
                val bySize = files.filter { it.size > 0 }.groupBy { it.size }.filter { it.value.size > 1 }
                val total = bySize.values.sumOf { it.size }.coerceAtLeast(1)
                var done = 0
                val groups = ArrayList<DuplicateGroup>()
                for ((size, candidates) in bySize) {
                    coroutineContext.ensureActive()
                    // 1) empreinte partielle (64 Ko) pour éliminer vite
                    val partial = candidates.groupBy { hash(context, it, 64 * 1024) }
                        .filter { it.key != null && it.value.size > 1 }
                    for ((_, group) in partial) {
                        // 2) empreinte complète si le fichier dépasse 64 Ko
                        val full = if (size <= 64 * 1024) mapOf("p" to group)
                        else group.groupBy { hash(context, it, Long.MAX_VALUE) }.filter { it.key != null }
                        full.values.filter { it.size > 1 }.forEach { groups += DuplicateGroup("$size", it) }
                    }
                    done += candidates.size
                    onProgress(done.toFloat() / total)
                }
                groups
            }
        }.sortedByDescending { it.wasted }
    }

    private val noise = listOf(
        Regex("""\s*\(\d+\)$"""), Regex("""\s*\[\d+\]$"""), Regex("""[\s_-]*(copy|copie|copia|kopie)(\s*\d+)?$"""),
        Regex("""^(copy of|copie de)\s+"""),
    )

    fun normalizeName(name: String): String {
        var n = name.substringBeforeLast('.').lowercase().trim()
        repeat(3) { noise.forEach { r -> n = n.replace(r, "").trim() } }
        return n.replace(Regex("""[^\p{L}\p{N}]+"""), "")
    }

    private fun open(context: Context, item: FileItem): InputStream? =
        if (item.path != null) File(item.path).inputStream()
        else context.contentResolver.openInputStream(Uri.parse(item.uri))

    private fun hash(context: Context, item: FileItem, limit: Long): String? = try {
        open(context, item)?.use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(128 * 1024)
            var read = 0L
            while (read < limit) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), limit - read).toInt())
                if (n <= 0) break
                md.update(buf, 0, n)
                read += n
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) {
        null
    }
}

/** Filtrage / tri / regroupement de la liste de fichiers. */
data class FileQuery(
    val text: String = "",
    val categories: Set<FileCategory> = emptySet(),
    val source: String? = null,
    val size: SizeFilter = SizeFilter.ALL,
    val date: DateFilter = DateFilter.ALL,
    val sort: SortField = SortField.DATE,
    val descending: Boolean = true,
    val group: GroupBy = GroupBy.NONE,
) {
    fun apply(files: List<FileItem>, now: Long = System.currentTimeMillis()): List<FileItem> {
        val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val day = 86_400_000L
        val filtered = files.filter { f ->
            (categories.isEmpty() || f.category in categories) &&
                (source == null || f.source == source) &&
                f.size >= size.min && f.size < size.max &&
                when {
                    date.days > 0 -> f.modified >= now - date.days * day
                    date.days < 0 -> f.modified < now + date.days * day
                    else -> true
                } &&
                (words.isEmpty() || words.all { w -> f.name.lowercase().contains(w) || f.folder.lowercase().contains(w) })
        }
        val cmp: Comparator<FileItem> = when (sort) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.DATE -> compareBy { it.modified }
            SortField.SIZE -> compareBy { it.size }
            SortField.TYPE -> compareBy<FileItem> { it.category.ordinal }.thenBy { it.extension }.thenBy { it.name.lowercase() }
            SortField.FOLDER -> compareBy<FileItem> { it.folder.lowercase() }.thenBy { it.name.lowercase() }
        }
        return filtered.sortedWith(if (descending) cmp.reversed() else cmp)
    }
}
