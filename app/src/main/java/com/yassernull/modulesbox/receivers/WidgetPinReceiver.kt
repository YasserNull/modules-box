package com.yassernull.modulesbox.receivers

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

// Logs when the launcher confirms a widget pin requested from the app.
//
// requestPinAppWidget() returning true only means the launcher accepted the
// request to show its pin UI; the launcher fires this callback once the widget
// is actually placed on the home screen. So it is the only reliable way to tell
// a real pin from a request the launcher silently dropped.
class WidgetPinReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.i("AddToWidget", "launcher CONFIRMED pin: appWidgetId=$id")

        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, ModuleWidgetProvider::class.java)
        Log.i("AddToWidget", "widget ids now bound to provider: ${manager.getAppWidgetIds(provider).toList()}")
    }
}
