package com.blackpool2026.muse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blackpool2026.muse.MainActivity
import com.blackpool2026.muse.R
import com.blackpool2026.muse.data.Covers
import com.blackpool2026.muse.data.Library
import com.blackpool2026.muse.data.Store
import com.blackpool2026.muse.player.PlaybackService
import com.blackpool2026.muse.ui.resolveTheme
import java.util.concurrent.Executors

/** Widget de escritorio: portada, título, artista y controles. Usa los colores del tema activo. */
class MuseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val p = prefs(context)
        val id = p.getLong("id", -1L)
        push(context, if (id >= 0) id else null, false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val a = intent.action ?: return
        if (a == ACT_PREV || a == ACT_TOGGLE || a == ACT_NEXT) handle(context, a)
    }

    private fun handle(ctx: Context, action: String) {
        val pending = goAsync()
        try {
            val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            val f = MediaController.Builder(ctx, token).buildAsync()
            f.addListener({
                try {
                    val c = f.get()
                    when (action) {
                        ACT_PREV -> if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
                        ACT_NEXT -> c.seekToNextMediaItem()
                        else -> {
                            if (c.mediaItemCount == 0) PlaybackService.instance?.playWhenReady()
                            else if (c.playWhenReady) c.pause()
                            else { c.prepare(); c.play() }
                        }
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        try { c.release() } catch (_: Throwable) {}
                        pending.finish()
                    }, 1500)
                } catch (_: Throwable) { pending.finish() }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (_: Throwable) { pending.finish() }
    }

    companion object {
        const val ACT_PREV = "com.blackpool2026.muse.W_PREV"
        const val ACT_TOGGLE = "com.blackpool2026.muse.W_TOGGLE"
        const val ACT_NEXT = "com.blackpool2026.muse.W_NEXT"

        private val exec = Executors.newSingleThreadExecutor()
        @Volatile private var cachedId: Long = -2
        @Volatile private var cachedBmp: Bitmap? = null

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

        fun push(ctx: Context, id: Long?, playing: Boolean) {
            val app = ctx.applicationContext
            exec.execute {
                try {
                    val song = if (id != null) {
                        Library.ensureLoaded(app)
                        Library.byId[id]
                    } else null
                    prefs(app).edit()
                        .putLong("id", id ?: -1L)
                        .putString("title", song?.title ?: "Muse")
                        .putString("artist", song?.artist ?: "Toca para abrir")
                        .putBoolean("playing", playing)
                        .apply()
                    val key = id ?: -1L
                    if (cachedId != key) {
                        cachedBmp = song?.let { Covers.get(app, it, 192) }?.let { rounded(it, 192) }
                            ?: Covers.placeholder(song?.title ?: "muse", 192).let { rounded(it, 192) }
                        cachedId = key
                    }
                    refresh(app)
                } catch (_: Throwable) {}
            }
        }

        private fun rounded(src: Bitmap, size: Int): Bitmap {
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = size * 0.16f
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val s = Bitmap.createScaledBitmap(src, size, size, true)
            c.drawBitmap(s, 0f, 0f, p)
            return out
        }

        private fun pi(ctx: Context, action: String, code: Int): PendingIntent {
            val i = Intent(ctx, MuseWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MuseWidget::class.java))
            if (ids.isEmpty()) return
            val p = prefs(ctx)
            val t = resolveTheme(Store.settings)
            val bg = androidx.compose.ui.graphics.lerp(t.bg1, t.bg2, 0.5f).toArgb() or (0xFF shl 24)
            val text = t.text.toArgb()
            val dim = t.textDim.toArgb()
            val accent = t.accent.toArgb()
            val playing = p.getBoolean("playing", false)

            val rv = RemoteViews(ctx.packageName, R.layout.widget_muse)
            rv.setInt(R.id.w_bg, "setColorFilter", bg)
            rv.setTextViewText(R.id.w_title, p.getString("title", "Muse"))
            rv.setTextViewText(R.id.w_artist, p.getString("artist", ""))
            rv.setTextColor(R.id.w_title, text)
            rv.setTextColor(R.id.w_artist, dim)
            rv.setInt(R.id.w_prev, "setColorFilter", text)
            rv.setInt(R.id.w_next, "setColorFilter", text)
            rv.setInt(R.id.w_play, "setColorFilter", accent)
            rv.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_muse_pause else R.drawable.ic_muse_play)
            cachedBmp?.let { rv.setImageViewBitmap(R.id.w_cover, it) }
            rv.setOnClickPendingIntent(R.id.w_prev, pi(ctx, ACT_PREV, 1))
            rv.setOnClickPendingIntent(R.id.w_play, pi(ctx, ACT_TOGGLE, 2))
            rv.setOnClickPendingIntent(R.id.w_next, pi(ctx, ACT_NEXT, 3))
            val open = PendingIntent.getActivity(
                ctx, 4, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_cover, open)
            rv.setOnClickPendingIntent(R.id.w_title, open)
            rv.setOnClickPendingIntent(R.id.w_artist, open)
            mgr.updateAppWidget(ids, rv)
        }
    }
}
