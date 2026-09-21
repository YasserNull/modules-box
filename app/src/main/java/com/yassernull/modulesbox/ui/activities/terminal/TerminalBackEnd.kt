package com.yassernull.modulesbox.ui.activities.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalFontSizeValue
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalHideSoftKeyboardIfHwd
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalKeepScreenOn
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalBell
import com.yassernull.modulesbox.ui.activities.TerminalActivity
import com.yassernull.modulesbox.ui.terminal.virtualkeys.SpecialButton
import com.yassernull.modulesbox.utils.dpToPxFloat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bridges a [TerminalView] with a service-backed [TerminalSession], implementing both
 * the view client and the session client. Faithful port from null-code-ide, minus the
 * blankj utilcode clipboard/keyboard helpers and the bell.oga asset.
 */
class TerminalBackEnd(
    val terminal: TerminalView,
    val activity: TerminalActivity,
    val onBackgroundColorArgb: Int,
    val backgroundColorArgb: Int,
    val cursorColorArgb: Int,
    val sessionId: String
) : TerminalViewClient, TerminalSessionClient {

    private var lastScaleFactor = 0f
    private val preferences = AppPreferences(activity)

    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val service = activity.sessionBinder?.getService() ?: return
        // The session name is set at creation (SessionService); skip unnamed sessions
        // so a null key can never reach the display-name map.
        val sessionId = changedSession.mSessionName ?: return
        val title = changedSession.title?.trim().orEmpty()
        if (title.isBlank()) {
            service.sessionDisplayNames.remove(sessionId)
            return
        }

        val maxLength = 24
        val displayTitle = if (title.length > maxLength) {
            title.take(maxLength - 3) + "..."
        } else {
            title
        }
        service.sessionDisplayNames[sessionId] = displayTitle
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val service = activity.sessionBinder?.getService() ?: return
        val finishedSessionName = finishedSession.mSessionName ?: return

        // Install sessions (module install scripts) are handled by the activity: it marks
        // the module installed/failed based on the session exit code. The activity returns
        // true when it took ownership of the event, so skip the generic toast below.
        if (activity.onInstallSessionFinished(finishedSession)) return

        val currentSessionName = service.currentSession.value.first
        if (currentSessionName != finishedSessionName) {
            activity.runOnUiThread {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.terminal_session_finished, finishedSessionName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText(activity.getString(R.string.terminal_clipboard_label), text)
        )
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString() ?: return
        if (clip.trim { it <= ' ' }.isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun onBell(session: TerminalSession) {
        // Bell sound intentionally not ported (modelbox has no bell.oga asset).
    }

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag.toString(), message.toString())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.toString(), message.toString())
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.toString(), message.toString())
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.toString(), message.toString())
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.toString(), message.toString())
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.printStackTrace()
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }

    override fun onScale(scale: Float): Float {
        val service = activity.sessionBinder?.getService() ?: return scale
        val currentSize = service.sessionFontSizes[sessionId] ?: preferences.getTerminalFontSizeValue()
        if (lastScaleFactor == 0f) {
            lastScaleFactor = scale
            return scale
        }

        val relative = (scale / lastScaleFactor).takeIf { it.isFinite() } ?: 1f
        lastScaleFactor = scale
        val rawSize = (currentSize * relative).coerceIn(11f, 45f)
        val steppedSize = (rawSize * 50f).roundToInt() / 50f
        if (abs(steppedSize - currentSize) < 0.01f) {
            return scale
        }

        terminal.setTextSize(dpToPxFloat(steppedSize, activity))
        service.sessionFontSizes[sessionId] = steppedSize
        return scale
    }

    val isHardwareKeyboardConnected: Boolean
        get() {
            val config = Resources.getSystem().configuration
            return config.keyboard != Configuration.KEYBOARD_NOKEYS
        }

    override fun onSingleTapUp(e: MotionEvent) {
        if (!(isHardwareKeyboardConnected && preferences.getTerminalHideSoftKeyboardIfHwd())) {
            showSoftInput()
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return true
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            activity.sessionBinder?.terminateSession(
                activity.sessionBinder!!.getService().currentSession.value.first
            )
            if (activity.sessionBinder!!.getService().sessionList.isEmpty()) {
                activity.finish()
            } else {
                changeSession(
                    activity,
                    activity.sessionBinder!!.getService().sessionList.keys.first(),
                    onBackgroundColorArgb,
                    backgroundColorArgb,
                    cursorColorArgb
                )
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    override fun readControlKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, false)
        return state != null && state
    }

    override fun readAltKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, false)
        return state != null && state
    }

    override fun readShiftKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, false)
        return state != null && state
    }

    override fun readFnKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, false)
        return state != null && state
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // Clear one-shot virtual modifier keys after a code point is sent.
        virtualKeysView.get()?.apply {
            readSpecialButton(SpecialButton.CTRL, true)
            readSpecialButton(SpecialButton.ALT, true)
            readSpecialButton(SpecialButton.SHIFT, true)
            readSpecialButton(SpecialButton.FN, true)
        }
        return false
    }

    override fun onEmulatorSet() {
        terminal.setTerminalCursorBlinkerRate(0)
        setTerminalCursorBlinkingState(false)
    }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(start, true)
        }
    }

    private fun showSoftInput() {
        terminal.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
    }
}
