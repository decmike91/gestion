package com.decmike.gestion.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Petit stockage clé → fichier JSON dans le dossier privé de l'application. */
class JsonStore(val dir: File) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    inline fun <reified T> load(name: String, default: T): T = try {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString<T>(f.readText()) else default
    } catch (e: Exception) {
        default
    }

    inline fun <reified T> save(name: String, value: T) {
        synchronized(this) {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(json.encodeToString(value))
            tmp.renameTo(File(dir, name))
        }
    }
}
