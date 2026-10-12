package com.yassernull.modulesbox.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppBrowser
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.utils.ModuleLauncher
import kotlinx.coroutines.launch

/**
 * Invisible entry point for widget taps.
 *
 * The widget used to fire a broadcast and start the activity from the receiver. That path
 * is a background activity start, which Android holds back (app-switch protection after
 * returning home) — the ~2 s delay users saw — and it also waited for the module server
 * before anything appeared. An activity PendingIntent is a foreground start, so it opens
 * instantly; the WebView then starts the module itself while showing a loader.
 * Alternative considered: keep the broadcast and start the server faster. It does not
 * help, because the delay comes from the background-start restriction itself.
 */
class ModuleLauncherActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ModuleLauncherActivity"
        private const val EXTRA_MODULE_ID = "module_id"

        fun intentForModule(context: Context, moduleId: String, widgetId: Int): Intent =
            Intent(context, ModuleLauncherActivity::class.java).apply {
                // Unique data per widget so PendingIntents for different widgets do not merge.
                data = Uri.parse("widget://$widgetId")
                putExtra(EXTRA_MODULE_ID, moduleId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets the home screen keep receiving touches while an external-browser launch
        // waits for the server.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID)
        if (moduleId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                launchModule(moduleId)
            } finally {
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private suspend fun launchModule(moduleId: String) {
        val browser = AppPreferences(applicationContext).getDefaultBrowserSync()
        Log.i(TAG, "widget launch: $moduleId via $browser")

        // The in-app WebView resolves the module and starts its server itself, behind a
        // loader. Doing it here too only added a second module scan before anything
        // appeared, so this path skips straight to the screen.
        if (browser == AppBrowser.MODULES_BOX) {
            ModuleWebViewActivity.launchForModule(this, moduleId)
            return
        }

        val context = applicationContext
        val module = ModuleLauncher.findModule(context, moduleId) ?: return
        val url = ModuleLauncher.ensureRunning(context, module)
        if (url == null) {
            Toast.makeText(context, getString(R.string.module_start_failed), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "no browser for $url", e)
            Toast.makeText(context, getString(R.string.error_opening_link, url), Toast.LENGTH_SHORT).show()
        }
    }
}
