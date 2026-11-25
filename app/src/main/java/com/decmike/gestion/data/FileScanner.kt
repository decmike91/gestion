package com.decmike.gestion.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/** Parcourt le stockage local et les drives (Storage Access Framework). */
class FileScanner(private val context: Context) {

    fun scanLocal(settings: AppSettings, onProgress: (Int) -> Unit): List<FileItem> {
        val storage = Environment.getExternalStorageDirectory()
        val roots = if (settings.scanWholeStorage) listOf(storage)
        else settings.localFolders.map { File(storage, it) }.filter { it.exists() }
        val excluded = setOf(File(storage, "Android/data").absolutePath, File(storage, "Android/obb").absolutePath)
        val seen = HashSet<String>()
        val out = ArrayList<FileItem>()
        for (root in roots) {
            root.walkTopDown()
                .maxDepth(16)
                .onEnter { dir ->
                    dir.absolutePath !in excluded && (settings.includeHidden || !dir.name.startsWith(".") || dir == root)
                }
                .onFail { _, _ -> }
                .forEach { f ->
                    if (!f.isFile) return@forEach
                    if (!settings.includeHidden && f.name.startsWith(".")) return@forEach
                    if (!seen.add(f.absolutePath)) return@forEach
                    out += fromFile(f, storage)
                    if (out.size % 200 == 0) onProgress(out.size)
                }
        }
        return out
    }

    private fun fromFile(f: File, storage: File): FileItem {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: ""
        val rel = f.absolutePath.removePrefix(storage.absolutePath).trimStart('/')
        val parent = f.parentFile
        return FileItem(
            uri = Uri.fromFile(f).toString(),
            name = f.name,
            displayPath = rel,
            folder = parent?.absolutePath?.removePrefix(storage.absolutePath)?.trimStart('/')?.ifEmpty { "/" } ?: "/",
            source = "Téléphone",
            sourceType = SourceType.LOCAL,
            size = f.length(),
            modified = f.lastModified(),
            mime = mime,
            category = FileCategory.of(f.name, mime),
            path = f.absolutePath,
        )
    }

    /** Parcours récursif d'un dossier/drive autorisé via OPEN_DOCUMENT_TREE. */
    fun scanTree(drive: DriveSource, includeHidden: Boolean, onProgress: (Int) -> Unit): List<FileItem> {
        val tree = Uri.parse(drive.uri)
        val out = ArrayList<FileItem>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add(rootId to "")
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        var visitedDirs = 0
        while (stack.isNotEmpty() && visitedDirs < 20_000) {
            val (docId, path) = stack.removeLast()
            visitedDirs++
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            try {
                queryChildren(children, projection)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (!includeHidden && name.startsWith(".")) continue
                        val mime = c.getString(2).orEmpty()
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.add(id to childPath)
                        } else {
                            out += FileItem(
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                name = name,
                                displayPath = "${drive.label}/$childPath",
                                folder = if (path.isEmpty()) drive.label else "${drive.label}/$path",
                                source = drive.label,
                                sourceType = SourceType.DRIVE,
                                size = if (c.isNull(3)) 0 else c.getLong(3),
                                modified = if (c.isNull(4)) 0 else c.getLong(4),
                                mime = mime,
                                category = FileCategory.of(name, mime),
                            )
                            if (out.size % 200 == 0) onProgress(out.size)
                        }
                    }
                }
            } catch (_: Exception) {
                // dossier illisible : on continue
            }
        }
        return out
    }

    /**
     * Les drives cloud (OneDrive, Dropbox…) répondent d'abord avec une liste partielle
     * marquée EXTRA_LOADING, puis la complètent : on réinterroge jusqu'à obtenir la liste complète.
     */
    private fun queryChildren(uri: Uri, projection: Array<String>): android.database.Cursor? {
        var attempt = 0
        while (true) {
            val c = context.contentResolver.query(uri, projection, null, null, null) ?: return null
            val loading = c.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
            if (!loading || attempt >= 20) return c
            c.close()
            attempt++
            Thread.sleep(500)
        }
    }

    companion object {
        fun providerName(uri: Uri): String {
            val a = uri.authority.orEmpty()
            return when {
                "skydrive" in a || "onedrive" in a -> "OneDrive"
                "dropbox" in a -> "Dropbox"
                "com.google.android.apps.docs" in a -> "Google Drive"
                "nextcloud" in a -> "Nextcloud"
                "owncloud" in a -> "ownCloud"
                "box" in a -> "Box"
                "mega" in a -> "MEGA"
                "pcloud" in a -> "pCloud"
                "com.android.externalstorage" in a -> "Stockage / USB / SD"
                "smb" in a || "cifs" in a -> "Réseau (SMB)"
                else -> a.ifEmpty { "Drive" }
            }
        }
    }
}
