package com.yassernull.modulesbox.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.core.LocaleManager
import com.yassernull.modulesbox.data.repository.ModuleRepository
import com.yassernull.modulesbox.data.repository.RepoRepository
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.ui.components.MainScreen
import com.yassernull.modulesbox.ui.theme.Theme
import com.yassernull.modulesbox.ui.viewmodels.*
import com.yassernull.modulesbox.utils.ModuleInstaller
import com.yassernull.modulesbox.utils.RuntimeTmp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var zipFileLauncher: ActivityResultLauncher<String>

    // Hoisted so the list can be refreshed in onResume when the user returns from a
    // Alpine install session (the install script only marks the module installed when
    // it completes successfully, so the list must reload after leaving the terminal).
    private val moduleViewModel: ModuleViewModel by viewModels {
        ModuleViewModelFactory(ModuleRepository(this))
    }

    // تطبيق اللغة قبل إنشاء الواجهة لضمان عرضها باللغة الصحيحة.
    override fun attachBaseContext(newBase: Context) {
        val appLanguage = runBlocking { LocaleManager.getSavedLanguage(newBase) }
        val localeContext = LocaleManager.applyLocaleToContext(newBase, appLanguage)
        super.attachBaseContext(localeContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val appPreferences = AppPreferences(this)
        val repoRepository = RepoRepository(this)

        // Create /opt/modules-box/.tmp and drop leftovers from the previous session.
        // Runs on the main process only — the shizuku shell service process also gets
        // its own Application instance and would otherwise wipe a live port list.
        lifecycleScope.launch { RuntimeTmp.prepare(this@MainActivity) }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(appPreferences))
            val repoViewModel: RepoViewModel = viewModel(factory = RepoViewModelFactory(repoRepository))

            // Launcher لاختيار ملف ZIP لتثبيت وحدة جديدة.
            zipFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    Toast.makeText(this, getString(R.string.installing_module), Toast.LENGTH_SHORT).show()
                    moduleViewModel.installModuleFromZip(uri, this) { module ->
                        runModuleInstall(module)
                    }
                }
            }

            // Launcher لمراقبة نتيجة العودة من شاشة الإعدادات.
            val languageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                // إذا تغيرت اللغة، أعد إنشاء الـ Activity لتطبيق التغيير.
                if (result.resultCode == RESULT_CODE_LANGUAGE_CHANGED) {
                    this.recreate()
                }
            }

            Theme(
                currentTheme = themeViewModel.currentTheme.value,
                isBlackThemeEnabled = themeViewModel.isBlackThemeEnabled.value,
                isMaterialYouEnabled = themeViewModel.isMaterialYouEnabled.value,
                hueShift = themeViewModel.hueShift.value,
                saturationShift = themeViewModel.saturationShift.value
            ) {
                HandleStoragePermission()

                MainScreen(
                    moduleViewModel = moduleViewModel,
                    repoViewModel = repoViewModel,
                    onSettingsClick = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        languageLauncher.launch(intent)
                        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
                    },
                    onInstallFromZipClick = { zipFileLauncher.launch("application/zip") },
                    onTerminalClick = {
                        val intent = Intent(this, TerminalActivity::class.java)
                        startActivity(intent)
                        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
                    },
                    onModuleNeedsInstall = { module ->
                        runModuleInstall(module)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload the installed modules whenever the user returns from the terminal so a
        // module whose install script just completed appears in the list. moduleViewModel
        // is a viewModels() delegate, so accessing it here returns the same activity-scoped
        // instance used by composition (creating it on first access if needed).
        moduleViewModel.refreshModules()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Leaving the app for good: remove the guest runtime files so nothing stale
        // survives into the next session. Rotation and plain backgrounding keep them,
        // since the servers are still running then.
        if (isFinishing && !isChangingConfigurations) {
            ModuleInstaller.onAppClosed(this)
        }
    }

    companion object {
        // رمز نتيجة مخصص للإشارة إلى أن اللغة قد تغيرت.
        const val RESULT_CODE_LANGUAGE_CHANGED = 1001
    }

    private fun runModuleInstall(module: Module) {
        ModuleInstaller.launchInstallTerminal(this, module)
    }
}
