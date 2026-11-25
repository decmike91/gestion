package com.decmike.gestion.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.decmike.gestion.GestionApp
import com.decmike.gestion.audio.PlaybackService
import com.decmike.gestion.audio.PlayerConnection.Companion.toMediaItem
import com.decmike.gestion.audio.Schedule
import java.util.Calendar

object ScheduleManager {
    private const val EXTRA_ID = "schedule_id"

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Prochain déclenchement (epoch ms) d'une programmation. */
    fun nextTrigger(s: Schedule, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, s.hour)
            set(Calendar.MINUTE, s.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            val candidate = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            if (candidate.timeInMillis <= now) continue
            val iso = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // lundi = 1
            if (s.days.isEmpty() || iso in s.days) return candidate.timeInMillis
        }
        return cal.timeInMillis + 86_400_000L
    }

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).putExtra(EXTRA_ID, id).setAction("gestion.schedule.$id")
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as GestionApp
        val am = context.getSystemService(AlarmManager::class.java)
        for (s in app.audio.state.value.schedules) {
            val pi = pending(context, s.id)
            am.cancel(pi)
            if (!s.enabled) continue
            val at = nextTrigger(s)
            try {
                if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    /** Lance la playlist programmée avec son profil et son ambiance. */
    fun fire(context: Context, id: String, done: () -> Unit) {
        val app = context.applicationContext as GestionApp
        val s = app.audio.state.value.schedules.firstOrNull { it.id == id }
        val playlist = s?.let { sc -> app.audio.state.value.playlists.firstOrNull { it.id == sc.playlistId } }
        if (s == null || playlist == null) { done(); return }

        app.audio.update { st ->
            st.copy(
                activeListenerId = s.listenerId ?: st.activeListenerId,
                activeAmbianceId = s.ambianceId ?: st.activeAmbianceId,
                // une programmation « une seule fois » se désactive après usage
                schedules = if (s.days.isEmpty()) st.schedules.map { if (it.id == s.id) it.copy(enabled = false) else it } else st.schedules,
            )
        }
        s.volume?.let { app.audio.setVolumePercent(it) }
        app.audio.flush()
        app.audio.sleepAt.value = s.stopAfterMinutes?.let { System.currentTimeMillis() + it * 60_000L }

        val tracks = app.audio.tracksOf(playlist).let { if (s.shuffle) it.shuffled() else it }
        if (tracks.isEmpty()) { rescheduleAll(context); done(); return }

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                c.prepare()
                c.play()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ c.release() }, 8_000)
            } catch (_: Exception) {
            } finally {
                rescheduleAll(context)
                done()
            }
        }, ContextCompat.getMainExecutor(app))
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("schedule_id") ?: return
        val result = goAsync()
        ScheduleManager.fire(context, id) { result.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScheduleManager.rescheduleAll(context)
    }
}
