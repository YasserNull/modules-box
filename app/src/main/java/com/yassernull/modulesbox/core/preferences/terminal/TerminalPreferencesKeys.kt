package com.yassernull.modulesbox.core.preferences.terminal

object TerminalPreferencesKeys {
    // Terminal Display
    const val TERMINAL_SHOW_TOOLBAR_KEY = "terminal_show_toolbar"
    const val TERMINAL_BACKGROUND_IMAGE_URI_KEY = "terminal_background_image_uri"

    // Terminal Font
    const val TERMINAL_FONT_KEY = "terminal_font"
    const val TERMINAL_CUSTOM_FONT_URI_KEY = "terminal_custom_font_uri"
    const val TERMINAL_FONT_SIZE_KEY = "terminal_font_size"

    // Terminal Behavior
    const val TERMINAL_BELL_KEY = "bell"
    const val TERMINAL_VIBRATE_KEY = "vibrate"
    const val TERMINAL_KEEP_SCREEN_ON_KEY = "keep_screen_on"
    const val TERMINAL_OPEN_ON_RUN_KEY = "open_terminal_on_run"
    const val TERMINAL_RUN_AFTER_BUILD_KEY = "run_after_build"
    const val TERMINAL_VIRTUAL_KEYS_KEY = "virtualKeys"
    const val TERMINAL_HIDE_SOFT_KEYBOARD_IF_HWD_KEY = "force_soft_keyboard"

    // Terminal Colors
    const val TERMINAL_TEXT_COLOR_KEY = "terminal_text_color"
    const val TERMINAL_TEXT_COLOR_SET_KEY = "terminal_text_color_set"
    const val TERMINAL_BACKGROUND_COLOR_KEY = "terminal_background_color"
    const val TERMINAL_BACKGROUND_COLOR_SET_KEY = "terminal_background_color_set"
    const val TERMINAL_CURSOR_COLOR_KEY = "terminal_cursor_color"
    const val TERMINAL_CURSOR_COLOR_SET_KEY = "terminal_cursor_color_set"
    const val TERMINAL_CUSTOM_COLOR_ENABLED_KEY = "terminal_custom_color_enabled_key"

    // Terminal Working Mode
    const val TERMINAL_WORKING_MODE_KEY = "workingMode"
    const val TERMINAL_ANDROID_ACCESS_MODE_KEY = "terminal_android_access_mode"
    const val TERMINAL_DISTRIBUTION_ACCESS_MODE_KEY = "terminal_distribution_access_mode"

    // Distribution permission: default/root (app-private storage) vs shizuku/root (/data/local/tmp/modules-box).
    const val TERMINAL_DISTRIBUTION_PERMISSION_KEY = "distribution_permission"

    // Whether the user has explicitly chosen a distribution permission.
    const val DISTRIBUTION_PERMISSION_CHOSEN_KEY = "distribution_permission_chosen"
}
