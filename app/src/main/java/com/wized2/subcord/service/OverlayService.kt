package com.wized2.subcord.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.wized2.subcord.MainActivity
import com.wized2.subcord.Prefs
import com.wized2.subcord.R
import com.wized2.subcord.SubcordApp

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var bubbleView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var translationsEnabled = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        if (Prefs(this).showBubble) attachBubble()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW -> {
                val t = intent.getStringExtra(EXTRA_TRANSLATED) ?: return START_STICKY
                showPanel(intent.getStringExtra(EXTRA_ORIGINAL), t)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SubcordApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_sort_alphabetically)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun layoutParams(width: Int, height: Int, y: Int = 120): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; this.y = y }
    }

    private fun attachBubble() {
        if (bubbleView != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        val params = layoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 400)
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 16
        var downX = 0; var downY = 0
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX.toInt(); downY = e.rawY.toInt(); true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX.toInt() - downX; val dy = e.rawY.toInt() - downY
                    params.x = (params.x - dx).coerceAtLeast(0)
                    params.y = (params.y + dy).coerceAtLeast(0)
                    downX = e.rawX.toInt(); downY = e.rawY.toInt()
                    windowManager.updateViewLayout(v, params); true
                }
                MotionEvent.ACTION_UP -> {
                    translationsEnabled = !translationsEnabled
                    view.findViewById<TextView>(R.id.bubble).alpha = if (translationsEnabled) 1f else 0.4f
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, params)
        bubbleView = view
    }

    private fun showPanel(original: String?, translated: String) {
        if (!translationsEnabled) return
        handler.post {
            val view = panelView ?: LayoutInflater.from(this).inflate(R.layout.overlay_panel, null).also {
                panelView = it
                windowManager.addView(it, layoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, 80))
            }
            view.findViewById<TextView>(R.id.overlayText).text = translated
            view.findViewById<TextView>(R.id.overlayTitle).text =
                if (original.isNullOrBlank()) "Subcord" else original.take(80)
            view.visibility = View.VISIBLE
            hideRunnable?.let { handler.removeCallbacks(it) }
            val hide = Runnable { view.visibility = View.GONE }
            hideRunnable = hide
            handler.postDelayed(hide, Prefs(this).panelDurationMs.toLong())
        }
    }

    override fun onDestroy() {
        instance = null
        hideRunnable?.let { handler.removeCallbacks(it) }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null; bubbleView = null
        Prefs(this).overlayEnabled = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_SHOW = "com.wized2.subcord.SHOW"
        const val ACTION_STOP = "com.wized2.subcord.STOP"
        const val EXTRA_TRANSLATED = "translated"
        const val EXTRA_ORIGINAL = "original"
        @Volatile private var instance: OverlayService? = null

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
            Prefs(context).overlayEnabled = true
        }
        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
            Prefs(context).overlayEnabled = false
        }
        fun showTranslation(context: Context, original: String, translated: String) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_SHOW)
                .putExtra(EXTRA_ORIGINAL, original).putExtra(EXTRA_TRANSLATED, translated)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
        fun isRunning(): Boolean = instance != null
    }
}
