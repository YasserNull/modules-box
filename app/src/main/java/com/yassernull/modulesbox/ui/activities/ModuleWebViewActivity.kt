package com.yassernull.modulesbox.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.core.LocaleManager
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.ui.theme.Theme
import com.yassernull.modulesbox.ui.viewmodels.ThemeViewModel
import com.yassernull.modulesbox.ui.viewmodels.ThemeViewModelFactory
import com.yassernull.modulesbox.utils.ModuleLauncher
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
        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID)

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
                    initialModuleDir = moduleDir,
                    script = script,
                    moduleId = moduleId
                )
            }
        }
    }

    companion object {
        private const val EXTRA_MODULE_DIR = "extra_module_dir"
        private const val EXTRA_SCRIPT = "extra_script"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MODULE_ID = "extra_module_id"

        /** Opens a module whose server is already running; [script] is its resolved URL. */
        fun launch(context: Context, module: Module, script: String) {
            context.startActivity(
                baseIntent(context, module.path, module.id, module.name).putExtra(EXTRA_SCRIPT, script)
            )
        }

        /** Opens immediately and starts the module's server (if needed) behind a loader. */
        fun launchForModule(context: Context, moduleId: String) {
            context.startActivity(baseIntent(context, null, moduleId, null))
        }

        private fun baseIntent(context: Context, moduleDir: String?, moduleId: String, title: String?): Intent =
            Intent(context, ModuleWebViewActivity::class.java).apply {
                // Same document key for both launch modes, so a widget tap reuses the
                // window opened from the app instead of creating a second one.
                data = Uri.parse("module://$moduleId")
                putExtra(EXTRA_MODULE_DIR, moduleDir)
                putExtra(EXTRA_MODULE_ID, moduleId)
                if (title != null) putExtra(EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
    }
}

@Composable
private fun ModuleWebViewScreen(
    initialModuleDir: String?,
    script: String?,
    moduleId: String?
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(script) }
    var moduleDir by remember { mutableStateOf(initialModuleDir) }
    var moduleIcon by remember { mutableStateOf<String?>(null) }
    var moduleName by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    if (url == null && moduleId != null && !failed) {
        LaunchedEffect(moduleId) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            Log.i("ModuleWebViewActivity", "resolve start: moduleId=$moduleId")
            val module = ModuleLauncher.findModule(context, moduleId)
            if (module == null) {
                Log.e("ModuleWebViewActivity", "module $moduleId is not installed")
                failed = true
                return@LaunchedEffect
            }
            moduleDir = module.path
            moduleIcon = module.icon
            moduleName = module.name
            val resolved = ModuleLauncher.ensureRunning(context, module)
            if (resolved == null) {
                Log.e("ModuleWebViewActivity", "could not start module $moduleId")
                failed = true
            } else {
                Log.i(
                    "ModuleWebViewActivity",
                    "url ready after ${android.os.SystemClock.elapsedRealtime() - startedAt}ms: $resolved"
                )
                url = resolved
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentUrl = url
        val currentDir = moduleDir
        Crossfade(targetState = currentUrl != null || failed || moduleId == null, label = "webview") { ready ->
            when {
                !ready -> ModuleLoadingScreen(
                    moduleDir = currentDir,
                    iconPath = moduleIcon,
                    name = moduleName ?: moduleId
                )
                currentDir != null && currentUrl != null -> {
                    val bgColor = MaterialTheme.colorScheme.background.value.toInt()
                    AndroidView(
                        factory = { ctx -> createWebView(ctx, currentDir, currentUrl, bgColor) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (failed) R.string.module_start_failed else R.string.script_not_found))
                }
            }
        }
    }
}

/**
 * Splash shown while a widget-tap module is starting: a themed background with the
 * module icon centered, fading in once it has been loaded from disk. A letter tile is
 * used when the module declares no icon.
 */
@Composable
private fun ModuleLoadingScreen(
    moduleDir: String?,
    iconPath: String?,
    name: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val iconFile = moduleDir?.let { dir ->
            iconPath?.takeIf { it.isNotBlank() }?.let { File(dir, it).takeIf { f -> f.exists() } }
        }

        if (iconFile != null) {
            var iconVisible by remember(iconFile) { mutableStateOf(false) }
            val painter = rememberAsyncImagePainter(
                model = iconFile,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) iconVisible = true
                }
            )
            AnimatedVisibility(
                visible = iconVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 350))
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (name ?: "M").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, moduleDir: String, script: String, backgroundColor: Int): WebView {
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
        // Prevent flash of white background before page loads by matching the app theme.
        setBackgroundColor(backgroundColor)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader?.shouldInterceptRequest(request.url)
            }

            override fun onPageStarted(view: WebView, webUrl: String?, favicon: android.graphics.Bitmap?) {
                Log.i("ModuleWebViewActivity", "onPageStarted: $webUrl")
            }

            override fun onPageFinished(view: WebView, webUrl: String?) {
                Log.i("ModuleWebViewActivity", "onPageFinished: $webUrl")
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.e("ModuleWebViewActivity", "onReceivedError: ${request.url} -> ${error.description}")
                }
            }
        }

        if (isHttp) {
            Log.i("ModuleWebViewActivity", "loadUrl: $script")
            loadUrl(script)
        } else {
            val relativePath = script.trimStart('/')
            val assetUrl = "https://$ASSETS_HOST/$ASSETS_PATH$relativePath"
            Log.i("ModuleWebViewActivity", "loadUrl: $assetUrl")
            loadUrl(assetUrl)
        }
    }
}
