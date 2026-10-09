package com.yassernull.modulesbox.core

enum class AppBrowser {
    MODULES_BOX,
    DEFAULT_BROWSER;

    companion object {
        fun fromString(value: String?): AppBrowser {
            return when (value) {
                DEFAULT_BROWSER.name -> DEFAULT_BROWSER
                else -> MODULES_BOX
            }
        }
    }
}
