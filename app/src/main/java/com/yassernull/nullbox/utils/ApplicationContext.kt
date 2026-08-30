package com.yassernull.nullbox.utils

import android.app.Application

/**
 * Global application instance, set once in [com.yassernull.nullbox.App.onCreate].
 * Used by utility helpers that need a Context outside of components.
 */
@JvmField
var application: Application? = null
