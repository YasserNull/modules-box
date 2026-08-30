package com.yassernull.nullbox.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import com.yassernull.nullbox.R
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.LocaleManager
import com.yassernull.nullbox.ui.theme.Theme
import com.yassernull.nullbox.ui.viewmodels.ThemeViewModel
import com.yassernull.nullbox.ui.viewmodels.ThemeViewModelFactory
import kotlinx.coroutines.runBlocking
import java.io.File

private const val ASSETS_HOST = "appassets.androidplatform.net"
private const val ASSETS_PATH = "modules/"

// يعرض ملف HTML الخاص بالوحدة (المشار إليه في خاصية "html") داخل WebView.
class ModuleWebViewActivity : ComponentActivity() {

    // تطبيق اللغة قبل إنشاء الواجهة.
    override fun attachBaseContext(newBase: Context) {
        val appLanguage = runBlocking { LocaleManager.getSavedLanguage(newBase) }
        val localeContext = LocaleManager.applyLocaleToContext(newBase, appLanguage)
        super.attachBaseContext(localeContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleDir = intent.getStringExtra(EXTRA_MODULE_DIR)
        val script = intent.getStringExtra(EXTRA_SCRIPT)

        val preferences = AppPreferences(this)
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(preferences))
            Theme(
                currentTheme = themeViewModel.currentTheme.value,
                isBlackThemeEnabled = themeViewModel.isBlackThemeEnabled.value,
                isMaterialYouEnabled = themeViewModel.isMaterialYouEnabled.value,
                hueShift = themeViewModel.hueShift.value,
                saturationShift = themeViewModel.saturationShift.value
            ) {
                ModuleWebViewScreen(
                    moduleDir = moduleDir,
                    script = script
                )
            }
        }
    }

    companion object {
        private const val EXTRA_MODULE_DIR = "extra_module_dir"
        private const val EXTRA_SCRIPT = "extra_script"
        private const val EXTRA_TITLE = "extra_title"

        fun launch(context: Context, moduleDir: String, script: String, title: String) {
            val intent = Intent(context, ModuleWebViewActivity::class.java)
                .putExtra(EXTRA_MODULE_DIR, moduleDir)
                .putExtra(EXTRA_SCRIPT, script)
                .putExtra(EXTRA_TITLE, title)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ModuleWebViewScreen(
    moduleDir: String?,
    script: String?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (moduleDir != null && script != null) {
            AndroidView(
                factory = { context -> createWebView(context, moduleDir, script) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.script_not_found))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, moduleDir: String, script: String): WebView {
    // يعمل مباشرة مع الروابط الخارجية، ويدير الملفات المحلية عبر WebViewAssetLoader.
    val isHttp = script.startsWith("http", ignoreCase = true)

    val assetLoader = if (isHttp) {
        null
    } else {
        WebViewAssetLoader.Builder()
            .addPathHandler("/$ASSETS_PATH", WebViewAssetLoader.InternalStoragePathHandler(context, File(moduleDir)))
            .build()
    }

    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader?.shouldInterceptRequest(request.url)
            }
        }

        if (isHttp) {
            loadUrl(script)
        } else {
            val relativePath = script.trimStart('/')
            loadUrl("https://$ASSETS_HOST/$ASSETS_PATH$relativePath")
        }
    }
}
