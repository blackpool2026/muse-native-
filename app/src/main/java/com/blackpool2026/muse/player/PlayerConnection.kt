package com.blackpool2026.muse.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blackpool2026.muse.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow

data class PlayerUi(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val mediaId: Long = -1,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val speed: Float = 1f
)

/** Puente entre la interfaz y el servicio de reproducción (MediaController). */
class PlayerConnection(private val ctx: Context) {
    val state = MutableStateFlow(PlayerUi())
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var dirty = true

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) dirty = true
            refresh()
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                dirty = true
                refresh()
            } catch (_: Throwable) { future = null }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        var queue = state.value.queue
        if (dirty) {
            val l = ArrayList<Long>(c.mediaItemCount)
            for (i in 0 until c.mediaItemCount) l.add(c.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L)
            queue = l
            dirty = false
        }
        state.value = PlayerUi(
            ready = true,
            playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE,
            mediaId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: -1,
            durationMs = if (c.duration > 0) c.duration else 0,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = queue,
            index = c.currentMediaItemIndex,
            speed = c.playbackParameters.speed
        )
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun playQueue(songs: List<Song>, start: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map { it.toMediaItem() }, start.coerceIn(0, songs.size - 1), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playQueue(songs, songs.indices.random(), true)
    }

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playQueue(songs, 0); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playQueue(songs, 0); return }
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun toggle() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { PlaybackService.instance?.playWhenReady(); return }
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause()
        else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }
    fun removeAt(index: Int) { controller?.removeMediaItem(index) }
    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }
    fun clearQueue() { controller?.let { it.stop(); it.clearMediaItems() } }
    fun setSpeed(s: Float) { controller?.setPlaybackSpeed(s) }
}
