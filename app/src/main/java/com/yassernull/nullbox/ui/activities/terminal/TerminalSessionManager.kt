package com.yassernull.nullbox.ui.activities.terminal

import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.yassernull.nullbox.R
import com.yassernull.nullbox.services.SessionService
import com.yassernull.nullbox.ui.activities.TerminalActivity
import com.yassernull.nullbox.utils.ShizukuShellHandle
import com.yassernull.nullbox.utils.distributionHomeDir
import com.yassernull.nullbox.utils.localBinDir
import com.yassernull.nullbox.utils.startShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages terminal session creation and lifecycle. Ported from null-code-ide with
 * chroot / distribution-shizuku removed.
 */
class TerminalSessionManager(
    private val activity: androidx.activity.ComponentActivity,
    private val sessionBinder: SessionService.SessionBinder?,
    private val scope: CoroutineScope,
    private val terminalTextColorArgb: Int,
    private val effectiveTerminalBackgroundColorArgb: Int,
    private val terminalCursorColorArgb: Int,
    private val onSessionCreated: ((String) -> Unit)? = null
) {

    /**
     * Creates a new terminal session based on the working mode.
     */
    fun createSession(workingMode: Int) {
        when (workingMode) {
            WorkingMode.SHIZUKU -> createShizukuSession()
            WorkingMode.DISTRIBUTION_SHIZUKU -> createDistributionShizukuSession()
            else -> createStandardSession(workingMode)
        }
    }

    /**
     * Creates a standard session (Android, Root, Distribution, Distribution-Root).
     */
    private fun createStandardSession(workingMode: Int) {
        val binder = sessionBinder ?: return
        val sessionLabel = activity.getString(R.string.session)
        val (sessionId, displayName) = generateUniqueSession(
            binder.getService().sessionList.keys.toList(),
            sessionLabel
        )

        if (activity is TerminalActivity) {
            terminalView.get()?.let {
                val client = TerminalBackEnd(
                    it,
                    activity,
                    terminalTextColorArgb,
                    effectiveTerminalBackgroundColorArgb,
                    terminalCursorColorArgb,
                    sessionId
                )
                binder.createSession(
                    sessionId,
                    client,
                    activity,
                    workingMode = workingMode
                )
                binder.getService().sessionDisplayNames[sessionId] = getSessionTitle(activity, workingMode, sessionId)

                changeSession(
                    activity,
                    session_id = sessionId,
                    onBackgroundColorArgb = terminalTextColorArgb,
                    backgroundColorArgb = effectiveTerminalBackgroundColorArgb,
                    cursorColorArgb = terminalCursorColorArgb
                )
            }
        } else {
            binder.getService().sessionDisplayNames[sessionId] = getSessionTitle(activity, workingMode, sessionId)
            onSessionCreated?.invoke(sessionId)
        }
    }

    /**
     * Creates a Shizuku-based session.
     */
    fun createShizukuSession() {
        createPrivilegedSession(WorkingMode.SHIZUKU)
    }

    /**
     * Creates a Distribution (Alpine proot) session via Shizuku.
     */
    fun createDistributionShizukuSession() {
        createPrivilegedSession(WorkingMode.DISTRIBUTION_SHIZUKU)
    }

    private fun createPrivilegedSession(workingMode: Int) {
        val binder = sessionBinder ?: return
        val sessionLabel = activity.getString(R.string.session)
        val (sessionId, displayName) = generateUniqueSession(
            binder.getService().sessionList.keys.toList(),
            sessionLabel
        )

        scope.launch(Dispatchers.IO) {
            try {
                val isDistro = workingMode == WorkingMode.DISTRIBUTION_SHIZUKU
                val shell = "/system/bin/sh"
                // The shizuku shell runs as the shell user, so the distro working dir and
                // init-host must live under /data/local/tmp/null-box (not app-private data).
                val home = if (isDistro) {
                    distributionHomeDir().path
                } else {
                    "/sdcard"
                }
                val args = if (isDistro) {
                    val initFile = File(localBinDir(), "init-host").absolutePath
                    val linker = if (File("/system/bin/linker64").exists()) {
                        "/system/bin/linker64"
                    } else {
                        "/system/bin/linker"
                    }
                    arrayOf(shell, "-c", "$linker $initFile proot")
                } else {
                    arrayOf(shell)
                }

                var handleRef: ShizukuShellHandle? = null
                val env = MkSession.buildAndroidEnv(activity, sessionId, workingMode)

                val (ptyInfo, handle) = startShizukuShell(
                    activity,
                    shell,
                    home,
                    args,
                    env,
                    24,
                    80,
                    0,
                    0
                ) { exitCode ->
                    activity.runOnUiThread {
                        binder.getSession(sessionId)?.notifyExternalProcessExit(exitCode)
                    }
                }
                handleRef = handle
                val ptyFd = ptyInfo.ptyFd.detachFd()

                withContext(Dispatchers.Main) {
                    val client = if (activity is TerminalActivity) {
                        val tv = terminalView.get()
                            ?: throw IllegalStateException("TerminalView not ready")
                        TerminalBackEnd(
                            tv,
                            activity,
                            terminalTextColorArgb,
                            effectiveTerminalBackgroundColorArgb,
                            terminalCursorColorArgb,
                            sessionId
                        )
                    } else {
                        // Placeholder client; a real one will be attached by the embedding UI.
                        object : TerminalSessionClient {
                            override fun onTextChanged(changedSession: TerminalSession) {}
                            override fun onTitleChanged(changedSession: TerminalSession) {}
                            override fun onSessionFinished(finishedSession: TerminalSession) {}
                            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
                            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
                            override fun onBell(session: TerminalSession) {}
                            override fun onColorsChanged(session: TerminalSession) {}
                            override fun onTerminalCursorStateChange(state: Boolean) {}
                            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                            override fun getTerminalCursorStyle(): Int =
                                TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
                            override fun logError(tag: String?, message: String?) {}
                            override fun logWarn(tag: String?, message: String?) {}
                            override fun logInfo(tag: String?, message: String?) {}
                            override fun logDebug(tag: String?, message: String?) {}
                            override fun logVerbose(tag: String?, message: String?) {}
                            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                            override fun logStackTrace(tag: String?, e: Exception?) {}
                        }
                    }

                    binder.createExternalSession(
                        sessionId,
                        client,
                        ptyFd,
                        ptyInfo.pid,
                        workingMode,
                        TerminalSession.ExternalProcessHandler { _ ->
                            handleRef?.stop()
                        }
                    )
                    binder.getService().sessionDisplayNames[sessionId] =
                        getSessionTitle(activity, workingMode, sessionId)

                    if (activity is TerminalActivity) {
                        changeSession(
                            activity,
                            session_id = sessionId,
                            onBackgroundColorArgb = terminalTextColorArgb,
                            backgroundColorArgb = effectiveTerminalBackgroundColorArgb,
                            cursorColorArgb = terminalCursorColorArgb
                        )
                    } else {
                        onSessionCreated?.invoke(sessionId)
                    }
                }
            } catch (e: Throwable) {
                Log.e("TerminalSessionManager", "Privileged session failed", e)
            }
        }
    }
}
