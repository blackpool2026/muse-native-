package com.blackpool2026.muse.player

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.blackpool2026.muse.MainActivity
import com.blackpool2026.muse.R
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.data.Store
import com.blackpool2026.muse.widget.MuseWidget
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        @Volatile var instance: PlaybackService? = null
        const val CMD_SHUFFLE = "muse.shuffle"
        const val CMD_FAV = "muse.fav"
        const val CHANNEL = "muse_playback"

        /** Momento (epoch ms) en que se apaga la música; 0 = temporizador desactivado. */
        val sleepEnd = MutableStateFlow(0L)

        fun setSleep(minutes: Int) {
            instance?.player?.volume = 1f
            sleepEnd.value = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        }
    }

    private var session: MediaSession? = null
    lateinit var player: ExoPlayer
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var counted = false
    private var lastSave = 0L
    private var playAfterRestore = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            counted = false
            pushWidget()
            updateLayout()
            saveSnap(false)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            saveSnap(true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            pushWidget()
            if (!isPlaying) saveSnap(false)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateLayout()
            saveSnap(false)
        }

        override fun onRepeatModeChanged(repeatMode: Int) { saveSnap(false) }

        override fun onAudioSessionIdChanged(audioSessionId: Int) { AudioFx.attach(audioSessionId) }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) AudioFx.attach(player.audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Archivo dañado o movido: pasar a la siguiente sin quedarse atascado.
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_FAV, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CMD_FAV -> {
                    player.currentMediaItem?.mediaId?.toLongOrNull()?.let { Store.toggleFav(it) }
                    updateLayout()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val id = item.mediaId.toLongOrNull()
                if (id != null) item.buildUpon()
                    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                    .build()
                else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val s = Store.settings
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = s.skipSilence
        player.setPlaybackSpeed(s.speed)
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .setBitmapLoader(MuseBitmapLoader(this))
            .build()

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL)
            .setChannelName(R.string.channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_notif)
        setMediaNotificationProvider(provider)

        updateLayout()
        AudioFx.attach(player.audioSessionId)
        handler.post(tick)
        restoreAsync()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSnap(false)
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        saveSnap(true)
        session?.run {
            player.release()
            release()
        }
        session = null
        AudioFx.release()
        instance = null
        super.onDestroy()
    }

    /** Reproduce; si la cola aún se está restaurando, espera a que termine. */
    fun playWhenReady() {
        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        } else playAfterRestore = true
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        if (player.isPlaying) {
            if (!counted && player.currentPosition > 15_000) {
                counted = true
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let { Store.incPlay(it) }
            }
            if (now - lastSave > 10_000) saveSnap(false)
        }
        val end = sleepEnd.value
        if (end > 0) {
            val left = end - now
            if (left <= 0) {
                player.pause()
                player.volume = 1f
                sleepEnd.value = 0L
            } else if (left < 10_000) {
                player.volume = (left / 10_000f).coerceIn(0f, 1f)
            }
        }
    }

    private fun pushWidget() {
        MuseWidget.push(this, player.currentMediaItem?.mediaId?.toLongOrNull(), player.isPlaying)
    }

    private fun updateLayout() {
        val shuffle = player.shuffleModeEnabled
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val fav = id != null && Store.favorites.contains(id)
        val b1 = CommandButton.Builder()
            .setDisplayName("Aleatorio")
            .setIconResId(if (shuffle) R.drawable.ic_n_shuffle_on else R.drawable.ic_n_shuffle)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .build()
        val b2 = CommandButton.Builder()
            .setDisplayName("Favorita")
            .setIconResId(if (fav) R.drawable.ic_n_fav_on else R.drawable.ic_n_fav)
            .setSessionCommand(SessionCommand(CMD_FAV, Bundle.EMPTY))
            .build()
        session?.setCustomLayout(ImmutableList.of(b1, b2))
    }

    private fun saveSnap(withIds: Boolean) {
        val n = player.mediaItemCount
        if (n == 0) return
        lastSave = System.currentTimeMillis()
        var ids: List<Long>? = null
        if (withIds) {
            val l = ArrayList<Long>(n)
            for (i in 0 until n) player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { l.add(it) }
            ids = l
        }
        Store.saveSnap(ids, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    private fun restoreAsync() {
        Thread {
            try {
                if (!Library.hasPermission(this)) return@Thread
                Library.ensureLoaded(this)
                val snap = Store.loadSnap() ?: return@Thread
                val songs = snap.ids.mapNotNull { Library.byId[it] }
                if (songs.isEmpty()) return@Thread
                val curId = snap.ids.getOrNull(snap.index)
                val idx = songs.indexOfFirst { it.id == curId }.coerceAtLeast(0)
                val items = songs.map { it.toMediaItem() }
                handler.post {
                    if (player.mediaItemCount == 0) {
                        player.setMediaItems(items, idx, snap.pos)
                        player.shuffleModeEnabled = snap.shuffle
                        player.repeatMode = snap.repeat
                        player.prepare()
                        if (playAfterRestore) { playAfterRestore = false; player.play() }
                    }
                }
            } catch (_: Throwable) {}
        }.start()
    }
}
