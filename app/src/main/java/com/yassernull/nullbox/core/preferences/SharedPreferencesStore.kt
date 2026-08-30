package com.yassernull.nullbox.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.yassernull.nullbox.core.AppPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TERMINAL_PREFS = "terminal"

internal fun AppPreferences.terminalPrefs(): SharedPreferences =
    context.getSharedPreferences(TERMINAL_PREFS, Context.MODE_PRIVATE)

internal fun <T> prefsFlow(prefs: SharedPreferences, reader: () -> T): Flow<T> {
    return callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(reader())
        }
        trySend(reader())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
