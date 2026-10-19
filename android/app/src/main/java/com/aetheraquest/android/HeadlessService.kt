package com.aetheraquest.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps an already authenticated AQW session alive without Compose or a render
 * loop. Login credentials are consumed from the start Intent and never written
 * to disk. Android's ongoing notification is the only status surface.
 */
class HeadlessService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: HeadlessSession? = null
    private var observer: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopHeadless()
                return START_NOT_STICKY
            }
            ACTION_START -> startHeadless(intent)
            else -> if (session == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startHeadless(intent: Intent) {
        val username = intent.getStringExtra(EXTRA_USERNAME)?.trim().orEmpty()
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val level = intent.getIntExtra(EXTRA_LEVEL, 1)
        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME)?.trim().orEmpty()
        val host = intent.getStringExtra(EXTRA_SERVER_HOST)?.trim().orEmpty()
        val port = intent.getIntExtra(EXTRA_SERVER_PORT, 0)
        val map = intent.getStringExtra(EXTRA_MAP)?.trim().orEmpty()
        if (username.isEmpty() || token.isEmpty() || serverName.isEmpty() || map.isEmpty() ||
            !AqwConfig.isTrustedGameHost(host) || port !in 1..65535
        ) {
            stopSelf()
            return
        }

        stopSession()
        val next = HeadlessSession()
        session = next
        HeadlessRegistry.publish(ConnectionStatus(ConnectionPhase.CONNECTING, "Starting headless mode…"))
        startForeground(NOTIFICATION_ID, notification(headlessNotification(
            ConnectionStatus(ConnectionPhase.CONNECTING, "Starting headless mode…"),
        )))
        foregroundStarted = true
        observer = scope.launch {
            var hasConnected = false
            next.status.collectLatest { status ->
                if (status.phase != ConnectionPhase.DISCONNECTED) hasConnected = true
                HeadlessRegistry.publish(status)
                notificationManager.notify(NOTIFICATION_ID, notification(headlessNotification(status)))
                if (shouldStopHeadless(status, hasConnected)) stopHeadless()
            }
        }
        next.start(
            LoginToken(username, token, level),
            AqwServer(serverName, host, port, online = true, full = false, upgradeOnly = false),
            map,
        )
    }

    private fun notification(model: HeadlessNotification): Notification {
        val open = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this, 2, Intent(this, HeadlessService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setContentIntent(openPending)
            .setOngoing(model.ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Headless session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "AQW connection status while visual rendering is off"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun stopSession() {
        observer?.cancel()
        observer = null
        session?.stop()
        session = null
    }

    private fun stopHeadless() {
        stopSession()
        HeadlessRegistry.clear()
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        stopSession()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "headless-session"
        private const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.aetheraquest.android.action.START_HEADLESS"
        const val ACTION_STOP = "com.aetheraquest.android.action.STOP_HEADLESS"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"
        const val EXTRA_MAP = "map"

        fun start(context: Context, login: LoginToken, server: AqwServer, map: String) {
            val intent = Intent(context, HeadlessService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_USERNAME, login.username)
                .putExtra(EXTRA_TOKEN, login.token)
                .putExtra(EXTRA_LEVEL, login.level)
                .putExtra(EXTRA_SERVER_NAME, server.name)
                .putExtra(EXTRA_SERVER_HOST, server.host)
                .putExtra(EXTRA_SERVER_PORT, server.port)
                .putExtra(EXTRA_MAP, map)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
