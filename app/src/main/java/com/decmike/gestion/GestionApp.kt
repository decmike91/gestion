package com.decmike.gestion

import android.app.Application
import com.decmike.gestion.audio.AudioRepository
import com.decmike.gestion.audio.PlayerConnection
import com.decmike.gestion.data.FileRepository
import com.decmike.gestion.data.JsonStore
import com.decmike.gestion.data.SettingsRepository
import com.decmike.gestion.schedule.ScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GestionApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var store: JsonStore
    lateinit var settings: SettingsRepository
    lateinit var files: FileRepository
    lateinit var audio: AudioRepository
    lateinit var player: PlayerConnection

    override fun onCreate() {
        super.onCreate()
        store = JsonStore(filesDir)
        settings = SettingsRepository(this, store)
        files = FileRepository(this, store, settings, scope)
        audio = AudioRepository(this, store, scope)
        player = PlayerConnection(this)

        // La bibliothèque musicale suit les fichiers analysés.
        scope.launch {
            files.files.collectLatest { audio.refreshLibrary(it) }
        }
        ScheduleManager.rescheduleAll(this)
    }
}
