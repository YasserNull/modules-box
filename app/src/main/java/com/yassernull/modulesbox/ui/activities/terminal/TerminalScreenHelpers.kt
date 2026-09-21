package com.yassernull.modulesbox.ui.activities.terminal

import android.graphics.Typeface
import androidx.compose.runtime.mutableStateOf
import com.termux.terminal.TextStyle as TermuxTextStyle
import com.termux.view.TerminalView
import com.yassernull.modulesbox.core.AppLanguage
import com.yassernull.modulesbox.core.LocaleManager
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.core.preferences.terminal.getEffectiveWorkingMode
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalFontSizeValue
import com.yassernull.modulesbox.core.preferences.terminal.getTerminalKeepScreenOn
import com.yassernull.modulesbox.ui.activities.TerminalActivity
import com.yassernull.modulesbox.ui.terminal.virtualkeys.VirtualKeysListener
import com.yassernull.modulesbox.ui.terminal.virtualkeys.VirtualKeysView
import com.yassernull.modulesbox.utils.application
import com.yassernull.modulesbox.utils.child
import com.yassernull.modulesbox.utils.dpToPxFloat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Helper functions and utilities for the terminal screen.
 */

/**
 * Generates a unique session ID and display name.
 */
fun generateUniqueSession(
    existingIds: List<String>,
    sessionLabel: String,
    preferredId: String? = null
): Pair<String, String> {
    fun isSafeId(value: String): Boolean {
        return value.all { it.isLetterOrDigit() || it == '_' }
    }

    fun extractTrailingNumber(value: String): Int? {
        val digits = value.takeLastWhile { it.isDigit() }
        return if (digits.isNotEmpty()) digits.toIntOrNull() else null
    }

    val preferredNumber = preferredId?.let { extractTrailingNumber(it) }
    if (preferredNumber != null) {
        val preferred = "session_$preferredNumber"
        if (preferred !in existingIds) {
            return preferred to "$sessionLabel $preferredNumber"
        }
    }

    if (preferredId != null && isSafeId(preferredId) && preferredId !in existingIds) {
        return preferredId to preferredId
    }

    var index = 1
    var id: String
    do {
        id = "session_$index"
        index++
    } while (id in existingIds)
    val displayName = "$sessionLabel ${index - 1}"
    return id to displayName
}

/**
 * Gets the localized display name for a session.
 */
fun getDisplayName(sessionId: String?, explicitName: String?, stringResource: (Int) -> String): String? {
    if (sessionId == null) return explicitName
    if (!sessionId.startsWith("session_")) return explicitName ?: sessionId

    val number = sessionId.removePrefix("session_")
    val localized = "${stringResource(com.yassernull.modulesbox.R.string.session)} $number"
    val englishDefault = "Session $number"

    return when {
        explicitName.isNullOrBlank() -> localized
        explicitName == sessionId -> localized
        explicitName == englishDefault -> localized
        else -> explicitName
    }
}

/**
 * Global reference to the terminal view.
 */
var terminalView = WeakReference<TerminalView?>(null)

/**
 * Global reference to the virtual keys view.
 */
var virtualKeysView = WeakReference<VirtualKeysView?>(null)

/**
 * Current background bitmap state.
 */
var bitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

/**
 * Gets the distribution name. Modelbox ships a single hardcoded Alpine Linux distribution.
 */
fun getDistributionName(context: android.content.Context): String {
    val isArabic = LocaleManager.currentAppLanguageState.value == AppLanguage.ARABIC
    return if (isArabic) "ألبين" else "Alpine Linux"
}

/**
 * Gets the session title (e.g., "Session 1").
 */
fun getSessionTitle(context: android.content.Context, workingMode: Int?, sessionId: String?): String {
    val isArabic = LocaleManager.currentAppLanguageState.value == AppLanguage.ARABIC
    val sessionStr = if (isArabic) "الجلسة" else "Session"
    val sessionNum = sessionId?.removePrefix("session_") ?: ""

    return if (sessionNum.isNotEmpty() && sessionNum.all { it.isDigit() }) {
        "$sessionStr $sessionNum"
    } else {
        "$sessionStr ?"
    }
}

/**
 * Gets the session description based on working mode.
 */
fun getSessionDescription(context: android.content.Context, workingMode: Int?): String {
    val distName = getDistributionName(context)
    val isArabic = LocaleManager.currentAppLanguageState.value == AppLanguage.ARABIC

    val androidStr = if (isArabic) "أندرويد" else "Android"
    val rootStr = if (isArabic) "جذر" else "Root"
    val shizukuStr = if (isArabic) "شيزيكو" else "Shizuku"
    val defaultStr = if (isArabic) "افتراضي" else "Default"
    val prootStr = if (isArabic) "بروت" else "proot"

    return when (workingMode) {
        WorkingMode.ANDROID -> "$androidStr $defaultStr"
        WorkingMode.ROOT -> "$androidStr $rootStr"
        WorkingMode.SHIZUKU -> "$androidStr $shizukuStr"
        WorkingMode.DISTRIBUTION -> "$distName $prootStr $defaultStr"
        WorkingMode.DISTRIBUTION_ROOT -> "$distName $prootStr $rootStr"
        WorkingMode.DISTRIBUTION_SHIZUKU -> "$distName $prootStr $shizukuStr"
        else -> "unknown"
    }
}

/**
 * Checks if the mode is a distribution (proot) mode.
 */
fun isDistributionMode(workingMode: Int?): Boolean {
    return workingMode == WorkingMode.DISTRIBUTION ||
        workingMode == WorkingMode.DISTRIBUTION_ROOT ||
        workingMode == WorkingMode.DISTRIBUTION_SHIZUKU
}

/**
 * Current font typeface file reference.
 */
val file = application!!.filesDir.child("font.ttf")

/**
 * Current font typeface.
 */
var font = (if (file.exists() && file.canRead()) {
    Typeface.createFromFile(file)
} else {
    Typeface.MONOSPACE
})

/**
 * Sets the terminal font typeface.
 */
suspend fun setFont(typeface: Typeface) = withContext(Dispatchers.Main) {
    font = typeface
    terminalView.get()?.apply {
        setTypeface(typeface)
        onScreenUpdated()
    }
}

/**
 * Toolbar visibility state.
 */
var showToolbar = mutableStateOf(true)

/**
 * Virtual keys visibility state.
 */
var showVirtualKeys = mutableStateOf(true)

/**
 * Latest drawer toggle callback for virtual keys.
 */
var onVirtualKeysToggleDrawer: (() -> Unit)? = null

/**
 * Hook set by the terminal composable so the activity can close the session drawer
 * from outside composition (e.g. from the back handler). Returns true if the drawer
 * was open and a close was requested.
 */
var closeTerminalDrawer: (() -> Boolean)? = null

/**
 * Changes the active terminal session.
 */
fun changeSession(
    terminalActivityActivity: TerminalActivity,
    session_id: String,
    onBackgroundColorArgb: Int,
    backgroundColorArgb: Int,
    cursorColorArgb: Int
) {
    val preferences = AppPreferences(terminalActivityActivity)
    terminalView.get()?.apply {
        val client = TerminalBackEnd(
            this,
            terminalActivityActivity,
            onBackgroundColorArgb,
            backgroundColorArgb,
            cursorColorArgb,
            session_id
        )
        val session =
            terminalActivityActivity.sessionBinder!!.getSession(session_id)
                ?: terminalActivityActivity.sessionBinder!!.createSession(
                    session_id,
                    client,
                    terminalActivityActivity,
                    workingMode = preferences.getEffectiveWorkingMode()
                )
        session.updateTerminalSessionClient(client)

        // Ensure renderer is ready before attaching to avoid NPE in updateSize()
        if (mRenderer == null) {
            // If renderer is not yet initialized, just set the session reference
            // It will be attached automatically when layout occurs
            mTermSession = session
        } else {
            attachSession(session)
        }

        setTerminalViewClient(client)
        val sessionFontSize =
            terminalActivityActivity.sessionBinder!!.getService().sessionFontSizes[session_id]
                ?: preferences.getTerminalFontSizeValue().toFloat()
        setTextSize(dpToPxFloat(sessionFontSize, terminalActivityActivity))
        post {
            requestFocus()
            isFocusableInTouchMode = true

            keepScreenOn = preferences.getTerminalKeepScreenOn()

            mEmulator?.mColors?.mCurrentColors?.apply {
                set(TermuxTextStyle.COLOR_INDEX_FOREGROUND, onBackgroundColorArgb)
                set(TermuxTextStyle.COLOR_INDEX_BACKGROUND, backgroundColorArgb)
                set(TermuxTextStyle.COLOR_INDEX_CURSOR, cursorColorArgb)
            }
            onScreenUpdated()
        }
        virtualKeysView.get()?.apply {
            val view = terminalView.get()
            virtualKeysViewClient =
                view?.let { VirtualKeysListener(it.mTermSession, it, onVirtualKeysToggleDrawer) }
        }
    }
    terminalActivityActivity.sessionBinder!!.getService().currentSession.value =
        Pair(session_id, terminalActivityActivity.sessionBinder!!.getService().sessionList[session_id]!!)
}

/**
 * Virtual keys configuration constant.
 */
const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"DRAWER\"," + "\n      \"display\": \"≡\"" + "\n    }," + "\n    {" + "\n      \"key\": \"SCROLL_LOCK\"," + "\n      \"display\": \"↑↓\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
