package com.decmike.gestion.audio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.decmike.gestion.GestionApp
import com.decmike.gestion.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Service de lecture en arrière-plan (notification, écran verrouillé, Bluetooth). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val effects = EffectsApplier()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as GestionApp
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId)
                effects.apply(app.audio.sound.value)
            }
        })
        effects.attach(player.audioSessionId)

        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId)
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        scope.launch { app.audio.sound.collect { effects.apply(it) } }
        scope.launch {
            app.audio.sleepAt.collect { at ->
                sleepJob?.cancel()
                if (at != null) sleepJob = launch {
                    delay((at - System.currentTimeMillis()).coerceAtLeast(0))
                    player.pause()
                    app.audio.sleepAt.value = null
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        effects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
