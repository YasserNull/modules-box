package com.yassernull.nullbox.core.preferences.terminal

import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.preferences.prefsFlow
import com.yassernull.nullbox.core.preferences.terminalPrefs
import com.yassernull.nullbox.ui.activities.terminal.DistroPermission
import com.yassernull.nullbox.ui.activities.terminal.WorkingMode
import com.yassernull.nullbox.utils.PrivilegedAccessManager
import kotlinx.coroutines.flow.Flow

// ==================== General Terminal Preferences ====================

fun AppPreferences.getTerminalShowToolbar(): Flow<Boolean> {
    return prefsFlow(terminalPrefs()) {
        terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_SHOW_TOOLBAR_KEY, true)
    }
}

fun AppPreferences.getTerminalShowToolbarValue(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_SHOW_TOOLBAR_KEY, true)
}

fun AppPreferences.setTerminalShowToolbarValue(show: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_SHOW_TOOLBAR_KEY, show).apply()
}

fun AppPreferences.getTerminalVirtualKeys(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_VIRTUAL_KEYS_KEY, true)
}

fun AppPreferences.setTerminalVirtualKeys(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_VIRTUAL_KEYS_KEY, enabled).apply()
}

fun AppPreferences.getTerminalBell(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_BELL_KEY, false)
}

fun AppPreferences.setTerminalBell(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_BELL_KEY, enabled).apply()
}

fun AppPreferences.getTerminalVibrate(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_VIBRATE_KEY, true)
}

fun AppPreferences.setTerminalVibrate(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_VIBRATE_KEY, enabled).apply()
}

fun AppPreferences.getTerminalKeepScreenOn(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_KEEP_SCREEN_ON_KEY, false)
}

fun AppPreferences.setTerminalKeepScreenOn(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_KEEP_SCREEN_ON_KEY, enabled).apply()
}

fun AppPreferences.getTerminalHideSoftKeyboardIfHwd(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_HIDE_SOFT_KEYBOARD_IF_HWD_KEY, true)
}

fun AppPreferences.setTerminalHideSoftKeyboardIfHwd(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_HIDE_SOFT_KEYBOARD_IF_HWD_KEY, enabled).apply()
}

fun AppPreferences.getTerminalOpenOnRun(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_OPEN_ON_RUN_KEY, true)
}

fun AppPreferences.setTerminalOpenOnRun(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_OPEN_ON_RUN_KEY, enabled).apply()
}

fun AppPreferences.getTerminalRunAfterBuild(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_RUN_AFTER_BUILD_KEY, true)
}

fun AppPreferences.setTerminalRunAfterBuild(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_RUN_AFTER_BUILD_KEY, enabled).apply()
}

// ==================== Terminal Colors ====================

fun AppPreferences.getTerminalTextColor(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_TEXT_COLOR_KEY, 0)
}

fun AppPreferences.setTerminalTextColor(color: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_TEXT_COLOR_KEY, color).apply()
}

fun AppPreferences.isTerminalTextColorSet(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_TEXT_COLOR_SET_KEY, false)
}

fun AppPreferences.setTerminalTextColorSet(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_TEXT_COLOR_SET_KEY, enabled).apply()
}

fun AppPreferences.getTerminalBackgroundColor(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_BACKGROUND_COLOR_KEY, 0)
}

fun AppPreferences.setTerminalBackgroundColor(color: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_BACKGROUND_COLOR_KEY, color).apply()
}

fun AppPreferences.isTerminalBackgroundColorSet(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_BACKGROUND_COLOR_SET_KEY, false)
}

fun AppPreferences.setTerminalBackgroundColorSet(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_BACKGROUND_COLOR_SET_KEY, enabled).apply()
}

fun AppPreferences.getTerminalCursorColor(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_CURSOR_COLOR_KEY, 0)
}

fun AppPreferences.setTerminalCursorColor(color: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_CURSOR_COLOR_KEY, color).apply()
}

fun AppPreferences.isTerminalCursorColorSet(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.TERMINAL_CURSOR_COLOR_SET_KEY, false)
}

fun AppPreferences.setTerminalCursorColorSet(enabled: Boolean) {
    terminalPrefs().edit().putBoolean(TerminalPreferencesKeys.TERMINAL_CURSOR_COLOR_SET_KEY, enabled).apply()
}

// ==================== Terminal Font ====================

fun AppPreferences.getTerminalFontSizeValue(): Float {
    return terminalPrefs().getFloat(TerminalPreferencesKeys.TERMINAL_FONT_SIZE_KEY, 13f)
}

fun AppPreferences.setTerminalFontSizeValue(size: Float) {
    terminalPrefs().edit().putFloat(TerminalPreferencesKeys.TERMINAL_FONT_SIZE_KEY, size).apply()
}

// ==================== Terminal Working Mode ====================

fun AppPreferences.setTerminalWorkingMode(mode: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_WORKING_MODE_KEY, mode).apply()
}

fun AppPreferences.getTerminalWorkingMode(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_WORKING_MODE_KEY, WorkingMode.DISTRIBUTION)
}

fun AppPreferences.getTerminalAndroidAccessMode(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_ANDROID_ACCESS_MODE_KEY, WorkingMode.ANDROID)
}

fun AppPreferences.setTerminalAndroidAccessMode(mode: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_ANDROID_ACCESS_MODE_KEY, mode).apply()
}

fun AppPreferences.getTerminalDistributionAccessMode(): Int {
    return terminalPrefs().getInt(TerminalPreferencesKeys.TERMINAL_DISTRIBUTION_ACCESS_MODE_KEY, WorkingMode.DISTRIBUTION)
}

fun AppPreferences.setTerminalDistributionAccessMode(mode: Int) {
    terminalPrefs().edit().putInt(TerminalPreferencesKeys.TERMINAL_DISTRIBUTION_ACCESS_MODE_KEY, mode).apply()
}

// ==================== Distribution Permission ====================

fun AppPreferences.getTerminalDistributionPermission(): Int {
    return terminalPrefs().getInt(
        TerminalPreferencesKeys.TERMINAL_DISTRIBUTION_PERMISSION_KEY,
        DistroPermission.DEFAULT_ROOT
    )
}

fun AppPreferences.setTerminalDistributionPermission(permission: Int) {
    terminalPrefs().edit()
        .putInt(TerminalPreferencesKeys.TERMINAL_DISTRIBUTION_PERMISSION_KEY, permission)
        .putBoolean(TerminalPreferencesKeys.DISTRIBUTION_PERMISSION_CHOSEN_KEY, true)
        .apply()
}

fun AppPreferences.isDistributionPermissionChosen(): Boolean {
    return terminalPrefs().getBoolean(TerminalPreferencesKeys.DISTRIBUTION_PERMISSION_CHOSEN_KEY, false)
}

/** True when the user chose shizuku/root, i.e. the distribution lives under /data/local/tmp/null-box. */
fun AppPreferences.isDistroShizukuRoot(): Boolean {
    return getTerminalDistributionPermission() == DistroPermission.SHIZUKU_ROOT
}

fun AppPreferences.getEffectiveWorkingMode(): Int {
    val effectiveDistribution = getEffectiveDistributionMode()
    val effectiveAndroid = getEffectiveAndroidMode()

    return if (getTerminalWorkingMode() == WorkingMode.DISTRIBUTION) effectiveDistribution else effectiveAndroid
}

fun AppPreferences.getEffectiveAndroidMode(): Int {
    val androidAccessMode = getTerminalAndroidAccessMode()

    return when (androidAccessMode) {
        WorkingMode.SHIZUKU -> if (PrivilegedAccessManager.hasShizukuPermission()) {
            WorkingMode.SHIZUKU
        } else {
            setTerminalAndroidAccessMode(WorkingMode.ANDROID)
            WorkingMode.ANDROID
        }

        WorkingMode.ROOT -> if (PrivilegedAccessManager.hasRootPermission()) {
            WorkingMode.ROOT
        } else {
            setTerminalAndroidAccessMode(WorkingMode.ANDROID)
            WorkingMode.ANDROID
        }

        else -> WorkingMode.ANDROID
    }
}

fun AppPreferences.getEffectiveDistributionMode(): Int {
    val distributionAccessMode = getTerminalDistributionAccessMode()

    // shizuku/root permission: only Shizuku and Root access are offered.
    if (isDistroShizukuRoot()) {
        return when (distributionAccessMode) {
            WorkingMode.DISTRIBUTION_ROOT -> if (PrivilegedAccessManager.hasRootPermission()) {
                WorkingMode.DISTRIBUTION_ROOT
            } else {
                setTerminalDistributionAccessMode(WorkingMode.DISTRIBUTION_SHIZUKU)
                WorkingMode.DISTRIBUTION_SHIZUKU
            }

            WorkingMode.DISTRIBUTION_SHIZUKU -> if (PrivilegedAccessManager.hasShizukuPermission()) {
                WorkingMode.DISTRIBUTION_SHIZUKU
            } else if (PrivilegedAccessManager.hasRootPermission()) {
                WorkingMode.DISTRIBUTION_ROOT
            } else {
                setTerminalDistributionAccessMode(WorkingMode.DISTRIBUTION_SHIZUKU)
                WorkingMode.DISTRIBUTION_SHIZUKU
            }

            else -> {
                // Fall back to Shizuku if available, otherwise root.
                when {
                    PrivilegedAccessManager.hasShizukuPermission() -> WorkingMode.DISTRIBUTION_SHIZUKU
                    PrivilegedAccessManager.hasRootPermission() -> WorkingMode.DISTRIBUTION_ROOT
                    else -> WorkingMode.DISTRIBUTION_SHIZUKU
                }
            }
        }
    }

    // default/root permission: only Default (proot) and Root access are offered.
    return if (distributionAccessMode == WorkingMode.DISTRIBUTION_ROOT &&
        !PrivilegedAccessManager.hasRootPermission()
    ) {
        setTerminalDistributionAccessMode(WorkingMode.DISTRIBUTION)
        WorkingMode.DISTRIBUTION
    } else if (distributionAccessMode == WorkingMode.DISTRIBUTION_ROOT) {
        WorkingMode.DISTRIBUTION_ROOT
    } else {
        WorkingMode.DISTRIBUTION
    }
}
