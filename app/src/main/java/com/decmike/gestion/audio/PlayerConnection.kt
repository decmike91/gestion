package com.decmike.gestion.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUi(
    val connected: Boolean = false,
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val currentUri: String? = null,
    val index: Int = 0,
    val count: Int = 0,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Connexion de l'interface au service de lecture via MediaController. */
class PlayerConnection(private val context: Context) {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect(onReady: ((MediaController) -> Unit)? = null) {
        controller?.let { c -> onReady?.invoke(c); return }
        onReady?.let { pending += it }
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try { future.get() } catch (e: Exception) { null } ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun refresh() {
        val c = controller ?: return
        val md = c.mediaMetadata
        _ui.value = PlayerUi(
            connected = true,
            hasMedia = c.mediaItemCount > 0,
            isPlaying = c.isPlaying,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            currentUri = c.currentMediaItem?.mediaId,
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            durationMs = c.duration.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    fun play(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false) = connect { c ->
        if (tracks.isEmpty()) return@connect
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, start.coerceIn(0, tracks.lastIndex), 0)
        c.prepare()
        c.play()
    }

    fun enqueue(tracks: List<Track>) = connect { c -> c.addMediaItems(tracks.map { it.toMediaItem() }) }

    fun toggle() = connect { c -> if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() } }
    fun next() = connect { it.seekToNextMediaItem() }
    fun previous() = connect { it.seekToPrevious() }
    fun seekTo(ms: Long) = connect { it.seekTo(ms) }
    fun setShuffle(on: Boolean) = connect { it.shuffleModeEnabled = on }
    fun cycleRepeat() = connect {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun stop() = connect { it.stop() }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    companion object {
        fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setGenre(genre.ifBlank { null })
                    .build()
            )
            .build()
    }
}
