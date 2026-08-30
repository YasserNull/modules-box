package com.yassernull.nullbox.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.yassernull.nullbox.R
import com.yassernull.nullbox.data.model.Module
import com.yassernull.nullbox.ui.activities.ModuleWebViewActivity
import com.yassernull.nullbox.ui.viewmodels.ModuleViewModel
import com.yassernull.nullbox.utils.ModuleInstaller
import com.yassernull.nullbox.utils.PortManager
import java.io.File

// المحتوى الرئيسي لشاشة الوحدات المثبتة.
@Composable
fun MainContent(
    viewModel: ModuleViewModel,
    searchQuery: String
) {
    val modules by viewModel.modules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // تصفية الوحدات بناءً على استعلام البحث.
    val filteredModules = if (searchQuery.isBlank()) {
        modules
    } else {
        modules.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.author.contains(searchQuery, ignoreCase = true) ||
            it.description?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    // استخدام rememberSwipeRefreshState من مكتبة Accompanist القديمة لتجنب أخطاء الترجمة
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    // حاوية تدعم السحب للتحديث.
    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = { viewModel.refreshModules() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && filteredModules.isEmpty()) {
            // عرض مؤشر التحميل في المنتصف.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredModules.isEmpty()) {
            // عرض رسالة في حالة عدم وجود وحدات أو نتائج بحث.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // جعلها قابلة للتمرير لتمكين السحب.
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val message = if (modules.isEmpty()) {
                    stringResource(id = R.string.no_modules_found)
                } else {
                    stringResource(id = R.string.no_search_results)
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            // عرض قائمة الوحدات.
            val context = LocalContext.current
            val scriptNotFoundMessage = stringResource(id = R.string.script_not_found)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = filteredModules, key = { it.id }) { module ->
                    ModuleItem(
                        module = module,
                        onModuleClick = {
                            openModule(context, module, scriptNotFoundMessage)
                        },
                        onStartClick = {
                            // زر التشغيل حالياً لا يفعل شيئاً
                        },
                        onDeleteClick = {
                            viewModel.uninstallModule(module)
                        }
                    )
                }
            }
        }
    }
}

/**
 * فتح الوحدة:
 * - إذا كان there HTML → تشغيل php -S localhost:$PORT وفتح http://localhost:$PORT/$HTML
 * - إذا لم يكن there HTML → تشغيل npm start
 */
private fun openModule(
    context: android.content.Context,
    module: Module,
    scriptNotFoundMessage: String
) {
    val html = module.html
    
    if (!html.isNullOrBlank()) {
        // الوحدة تحتوي على ملف HTML → تشغيل PHP server
        startPhpServerAndOpen(context, module, html)
    } else {
        // الوحدة لا تحتوي على HTML → تشغيل npm start
        startWebServerModule(context, module)
    }
}

/**
 * تشغيل PHP server وفتح الملف في WebView.
 */
private fun startPhpServerAndOpen(
    context: android.content.Context,
    module: Module,
    html: String
) {
    // التحقق من وجود ملف HTML
    val htmlFile = File(module.path, html)
    if (!htmlFile.exists()) {
        Toast.makeText(context, "ملف HTML غير موجود: $html", Toast.LENGTH_SHORT).show()
        return
    }

    // تشغيل PHP server
    val port = ModuleInstaller.startPhpServer(context, module)
    if (port == null) {
        Toast.makeText(context, "فشل في تشغيل الخادم", Toast.LENGTH_SHORT).show()
        return
    }

    // فتح الرابط في WebView
    val url = "http://localhost:$port/$html"
    ModuleWebViewActivity.launch(context, module.path, url, module.name)
}

/**
 * تشغيل وحدة خادم ويب (npm start) في بيئة Alpine مع بورت عشوائي.
 */
private fun startWebServerModule(
    context: android.content.Context,
    module: Module
) {
    // توليد بورت عشوائي
    val port = PortManager.generateAvailablePort(context)
    if (port == null) {
        Toast.makeText(context, "لا يوجد بورت متاح", Toast.LENGTH_SHORT).show()
        return
    }
    
    // تسجيل البورت
    if (!PortManager.acquirePort(context, port)) {
        Toast.makeText(context, "فشل في تخصيص البورت", Toast.LENGTH_SHORT).show()
        return
    }
    
    // تشغيل npm start في بيئة Alpine
    ModuleInstaller.runInstallScript(context, module)
}
