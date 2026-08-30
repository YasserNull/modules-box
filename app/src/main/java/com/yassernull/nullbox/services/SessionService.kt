package com.yassernull.nullbox.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.yassernull.nullbox.R
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.preferences.terminal.getEffectiveWorkingMode
import com.yassernull.nullbox.core.preferences.terminal.getTerminalFontSizeValue
import com.yassernull.nullbox.ipc.RishDaemon
import com.yassernull.nullbox.ui.activities.TerminalActivity
import com.yassernull.nullbox.ui.activities.terminal.MkSession
import com.yassernull.nullbox.ui.activities.terminal.WorkingMode

class SessionService : Service() {
    private val prefs by lazy { AppPreferences(this) }
    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateMapOf<String, Int>()
    val sessionDisplayNames = mutableStateMapOf<String, String>()
    val sessionFontSizes = mutableStateMapOf<String, Float>()
    var currentSession = mutableStateOf(
        Pair("session_0", WorkingMode.DISTRIBUTION)
    )

    inner class SessionBinder : Binder() {
        fun getService(): SessionService {
            return this@SessionService
        }

        fun terminateAllSessions() {
            sessions.values.forEach {
                it.finishIfRunning()
            }
            sessions.clear()
            sessionList.clear()
            sessionFontSizes.clear()
            updateNotification()
        }

        fun createSession(id: String, client: TerminalSessionClient, activity: Activity, workingMode: Int): TerminalSession {
            return MkSession.createSession(activity, client, id, workingMode = workingMode).also {
                it.mSessionName = id
                sessions[id] = it
                sessionList[id] = workingMode
                if (!sessionFontSizes.containsKey(id)) {
                    sessionFontSizes[id] = prefs.getTerminalFontSizeValue()
                }
                if (id.startsWith("session_") && !sessionDisplayNames.containsKey(id)) {
                    val number = id.removePrefix("session_")
                    sessionDisplayNames[id] = getString(R.string.session) + " " + number
                }
                updateNotification()
            }
        }

        fun createExternalSession(
            id: String,
            client: TerminalSessionClient,
            ptyFd: Int,
            pid: Int,
            workingMode: Int,
            externalHandler: TerminalSession.ExternalProcessHandler
        ): TerminalSession {
            val session = TerminalSession(
                ptyFd,
                pid,
                "/sdcard",
                emptyArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                client,
                externalHandler
            )
            session.mSessionName = id
            sessions[id] = session
            sessionList[id] = workingMode
            if (!sessionFontSizes.containsKey(id)) {
                sessionFontSizes[id] = prefs.getTerminalFontSizeValue()
            }
            if (id.startsWith("session_") && !sessionDisplayNames.containsKey(id)) {
                val number = id.removePrefix("session_")
                sessionDisplayNames[id] = getString(R.string.session) + " " + number
            }
            updateNotification()
            return session
        }

        fun getSession(id: String): TerminalSession? {
            return sessions[id]
        }

        fun terminateSession(id: String) {
            runCatching {
                sessions[id]?.apply {
                    if (emulator != null) {
                        sessions[id]?.finishIfRunning()
                    }
                }

                sessions.remove(id)
                sessionList.remove(id)
                sessionDisplayNames.remove(id)
                sessionFontSizes.remove(id)
                if (sessions.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.forEach { s -> s.value.finishIfRunning() }
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        currentSession.value = Pair("session_0", prefs.getEffectiveWorkingMode())
        RishDaemon.start(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                sessions.forEach { s -> s.value.finishIfRunning() }
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_terminal))
            .setContentText(getNotificationContentText())
            .setSmallIcon(R.drawable.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    getString(R.string.notification_action_exit),
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name_session_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description_session_service)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        if (count == 1) {
            return getString(R.string.notification_session_count_one)
        }
        return getString(R.string.notification_session_count_plural, count)
    }
}
