package com.yassernull.nullbox.ui.activities

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yassernull.nullbox.R
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.LocaleManager
import com.yassernull.nullbox.data.repository.ModuleRepository
import com.yassernull.nullbox.data.repository.RepoRepository
import com.yassernull.nullbox.ui.components.MainScreen
import com.yassernull.nullbox.ui.theme.Theme
import com.yassernull.nullbox.ui.viewmodels.*
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var zipFileLauncher: ActivityResultLauncher<String>

    // تطبيق اللغة قبل إنشاء الواجهة لضمان عرضها باللغة الصحيحة.
    override fun attachBaseContext(newBase: Context) {
        val appLanguage = runBlocking { LocaleManager.getSavedLanguage(newBase) }
        val localeContext = LocaleManager.applyLocaleToContext(newBase, appLanguage)
        super.attachBaseContext(localeContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appPreferences = AppPreferences(this)
        val moduleRepository = ModuleRepository(this)
        val repoRepository = RepoRepository(this)

        setContent {
            val moduleViewModel: ModuleViewModel = viewModel(factory = ModuleViewModelFactory(moduleRepository))
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(appPreferences))
            val repoViewModel: RepoViewModel = viewModel(factory = RepoViewModelFactory(repoRepository))

            // Launcher لاختيار ملف ZIP لتثبيت وحدة جديدة.
            zipFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    Toast.makeText(this, getString(R.string.installing_module), Toast.LENGTH_SHORT).show()
                    moduleViewModel.installModuleFromZip(uri, this)
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
                    }
                )
            }
        }
    }

    companion object {
        // رمز نتيجة مخصص للإشارة إلى أن اللغة قد تغيرت.
        const val RESULT_CODE_LANGUAGE_CHANGED = 1001
    }
}
