package com.yassernull.modulesbox.ui.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.repository.ModuleRepository
import com.yassernull.modulesbox.receivers.ModuleWidgetProvider
import com.yassernull.modulesbox.ui.theme.Theme
import com.yassernull.modulesbox.core.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModuleWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setResult(Activity.RESULT_CANCELED)
        
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val appPreferences = AppPreferences(this)
        
        setContent {
            val themeViewModel: com.yassernull.modulesbox.ui.viewmodels.ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = com.yassernull.modulesbox.ui.viewmodels.ThemeViewModelFactory(appPreferences))
            
            Theme(
                currentTheme = themeViewModel.currentTheme.value,
                isBlackThemeEnabled = themeViewModel.isBlackThemeEnabled.value,
                isMaterialYouEnabled = themeViewModel.isMaterialYouEnabled.value,
                hueShift = themeViewModel.hueShift.value,
                saturationShift = themeViewModel.saturationShift.value
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetConfigureScreen(
                        onModuleSelected = { module, showName ->
                            saveWidgetConfig(module, showName)
                        }
                    )
                }
            }
        }
    }

    private fun saveWidgetConfig(module: Module, showName: Boolean) {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("widget_${appWidgetId}_id", module.id)
            .putString("widget_${appWidgetId}_name", module.name)
            .putString("widget_${appWidgetId}_icon", module.icon)
            .putString("widget_${appWidgetId}_path", module.path)
            .putBoolean("widget_${appWidgetId}_show_name", showName)
            .apply()

        val appWidgetManager = AppWidgetManager.getInstance(this)
        ModuleWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigureScreen(onModuleSelected: (Module, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modules = remember { mutableStateOf<List<Module>>(emptyList()) }
    val showName = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val repo = ModuleRepository(context)
        withContext(Dispatchers.IO) {
            modules.value = repo.getModules()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.select_module_for_widget) ?: "Select Module") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.show_widget_name) ?: "Show module name",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showName.value,
                    onCheckedChange = { showName.value = it }
                )
            }
            
            Divider(modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(modules.value) { module ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onModuleSelected(module, showName.value) }
                    ) {
                        Text(
                            text = module.name,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (modules.value.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(context.getString(R.string.no_modules_installed) ?: "No modules installed")
                        }
                    }
                }
            }
        }
    }
}
