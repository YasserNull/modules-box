package com.yassernull.modulesbox.data.repository

import android.content.Context
import android.util.Log
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.model.RemoteModule
import com.yassernull.modulesbox.utils.PrivilegedFileOps
import com.yassernull.modulesbox.utils.distributionDir
import com.yassernull.modulesbox.utils.isDistroShizukuRoot
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

// كائن يمثل نتيجة عملية جلب المستودع.
data class RepoFetchResult(
    val modules: List<RemoteModule>? = null, // قائمة الوحدات في حالة النجاح
    val rawResponse: String? = null,        // الاستجابة الخام للمساعدة في التصحيح
    val errorMessage: String? = null        // رسالة الخطأ في حالة الفشل
)

/**
 * Result of downloading a module from the remote repository.
 *  - [Downloaded]: files are in place and the module is ready (no install script, or the
 *    script is missing so it counts as installed).
 *  - [NeedsTerminalInstall]: the module declares an install script that must run in a
 *    Debian terminal before it counts as installed.
 *  - [Failed]: the download could not be completed.
 */
sealed class DownloadModuleResult {
    object Downloaded : DownloadModuleResult()
    data class NeedsTerminalInstall(val module: Module) : DownloadModuleResult()
    object Failed : DownloadModuleResult()
}

// مستودع لإدارة التفاعل مع المستودع عن بعد (جلب القائمة وتنزيل الوحدات).
//
// الفكرة: منظمة modules-box-repo على GitHub يشارك فيها الكل وحداته — كل وحدة في
// مستودع مستقل. نجلب أسماء المستودعات من API المنظمة (مع ترقيم الصفحات)، ثم نقرأ
// ملف module.prop والأيقونة من كل مستودع.
class RepoRepository(private val context: Context) {

    private val modulesRootPath by lazy { File(context.filesDir, "modules").absolutePath }
    private val client = HttpClient(Android)

    companion object {
        const val ORG = "modules-box-repo"
        private const val TAG = "RepoRepository"
        private const val API_PAGE_SIZE = 100
        private val BRANCHES = listOf("main", "master")
    }

    // جلب قائمة الوحدات: أسماء مستودعات المنظمة ثم module.prop لكل واحد.
    suspend fun getRepoModules(): RepoFetchResult {
        return withContext(Dispatchers.IO) {
            try {
                val names = fetchOrgRepoNames()
                val modules = coroutineScope {
                    names.map { name ->
                        async { fetchRemoteModule(name) }
                    }.awaitAll().filterNotNull()
                }
                RepoFetchResult(modules = modules)
            } catch (e: Exception) {
                Log.w(TAG, "getRepoModules failed", e)
                RepoFetchResult(errorMessage = "repo_load_failed")
            }
        }
    }

    // أسماء كل مستودعات المنظمة العامة مع ترقيم الصفحات.
    private suspend fun fetchOrgRepoNames(): List<String> {
        val names = mutableListOf<String>()
        var page = 1
        while (true) {
            val url = "https://api.github.com/users/$ORG/repos?per_page=$API_PAGE_SIZE&page=$page"
            val body = client.get(url).bodyAsText()
            val array = JSONArray(body)
            if (array.length() == 0) break
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { names.add(it) }
            }
            if (array.length() < API_PAGE_SIZE) break
            page++
        }
        return names
    }

    // قراءة module.prop والأيقونة من مستودع واحد. رابط المستودع المشتق من القائمة
    // هو المرجع (وليس حقل repository داخل الملف).
    private suspend fun fetchRemoteModule(repoName: String): RemoteModule? {
        return try {
            val repoUrl = "https://github.com/$ORG/$repoName"
            // النص مع الفرع الذي نجح (main ثم master) لبناء رابط README لاحقاً.
            val (propText, branch) = downloadRawText(repoUrl, "module.prop") ?: return null
            val props = Properties()
            props.load(propText.reader())
            val moduleId = props.getProperty("id") ?: return null
            val iconFile = props.getProperty("icon")
            val iconPath = if (!iconFile.isNullOrBlank()) {
                downloadIcon(repoUrl, moduleId, iconFile)
            } else {
                null
            }
            RemoteModule(
                id = moduleId,
                name = props.getProperty("name") ?: moduleId,
                description = props.getProperty("description") ?: "",
                author = props.getProperty("author") ?: "",
                version = props.getProperty("version") ?: "",
                versionCode = props.getProperty("versionCode") ?: "",
                repository = repoUrl,
                iconPath = iconPath,
                readmeUrl = "$repoUrl/blob/$branch/README.md",
                downloads = fetchReleaseDownloads(repoName)
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchRemoteModule failed: $repoName", e)
            null
        }
    }

    // مجموع تحميلات كل ملفات كل النسخ (releases) — عدد تحميلات الوحدة.
    private suspend fun fetchReleaseDownloads(repoName: String): Long {
        var total = 0L
        var page = 1
        try {
            while (true) {
                val url = "https://api.github.com/repos/$ORG/$repoName/releases?per_page=$API_PAGE_SIZE&page=$page"
                val array = JSONArray(client.get(url).bodyAsText())
                if (array.length() == 0) break
                for (i in 0 until array.length()) {
                    val assets = array.optJSONObject(i)?.optJSONArray("assets") ?: continue
                    for (j in 0 until assets.length()) {
                        total += assets.optJSONObject(j)?.optLong("download_count", 0) ?: 0
                    }
                }
                if (array.length() < API_PAGE_SIZE) break
                page++
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchReleaseDownloads failed: $repoName", e)
        }
        return total
    }

    // تنزيل أيقونة الوحدة إلى الكاش لعرضها في القائمة.
    private suspend fun downloadIcon(repoUrl: String, moduleId: String, iconFile: String): String? {
        return try {
            val bytes = downloadRawBytes(repoUrl, iconFile) ?: return null
            val dir = File(context.cacheDir, "repo_icons").apply { mkdirs() }
            val safeName = iconFile.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "icon"
            val out = File(dir, "${moduleId}_$safeName")
            out.writeBytes(bytes)
            out.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "downloadIcon failed: $iconFile", e)
            null
        }
    }

    // بناء رابط الملف الخام على GitHub.
    private fun getRawFileUrl(repoUrl: String, branch: String, fileName: String): String {
        val path = repoUrl.removePrefix("https://github.com/")
        return "https://raw.githubusercontent.com/$path/$branch/$fileName"
    }

    // جلب نص ملف من المستودع مع الفرع الناجح (main ثم master).
    private suspend fun downloadRawText(repoUrl: String, fileName: String): Pair<String, String>? {
        for (branch in BRANCHES) {
            try {
                val response: HttpResponse = client.get(getRawFileUrl(repoUrl, branch, fileName))
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    if (text.isNotBlank()) return text to branch
                }
            } catch (e: Exception) {
                Log.w(TAG, "downloadRawText failed: $fileName ($branch)", e)
            }
        }
        return null
    }

    // جلب ملف ثنائي من المستودع (main ثم master).
    private suspend fun downloadRawBytes(repoUrl: String, fileName: String): ByteArray? {
        for (branch in BRANCHES) {
            try {
                val response: HttpResponse = client.get(getRawFileUrl(repoUrl, branch, fileName))
                if (response.status.isSuccess()) {
                    val bytes: ByteArray = response.body()
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (e: Exception) {
                Log.w(TAG, "downloadRawBytes failed: $fileName ($branch)", e)
            }
        }
        return null
    }

    // تنزيل ملفات وحدة معينة من مستودعها على GitHub.
    suspend fun downloadModule(remoteModule: RemoteModule): DownloadModuleResult {
        return withContext(Dispatchers.IO) {
            try {
            	triggerVisitorBadge(remoteModule)
                // تنزيل module.prop أولاً للحصول على المعرف.
                val modulePropContent = downloadRawText(remoteModule.repository, "module.prop")?.first
                if (modulePropContent.isNullOrEmpty()) return@withContext DownloadModuleResult.Failed

                val props = Properties()
                props.load(modulePropContent.reader())
                val moduleId = props.getProperty("id") ?: return@withContext DownloadModuleResult.Failed

                val moduleDir = File(modulesRootPath, moduleId)
                if (!moduleDir.exists()) moduleDir.mkdirs()

                // حفظ الملفات التي تم تنزيلها.
                saveContentToFile(modulePropContent, File(moduleDir, "module.prop"))

                // تنزيل ملف HTML المشار إليه في خاصية "html".
                val htmlPath = props.getProperty("html")
                if (!htmlPath.isNullOrBlank()) {
                    downloadAndSaveFile(remoteModule, htmlPath, moduleDir)
                }

                // تنزيل سكربت التثبيت المشار إليه في خاصية "install".
                val installPath = props.getProperty("install")
                if (!installPath.isNullOrBlank()) {
                    downloadAndSaveFile(remoteModule, installPath, moduleDir)
                }

                // تنزيل أيقونة الوحدة المشار إليها في خاصية "icon".
                val iconPath = props.getProperty("icon")
                if (!iconPath.isNullOrBlank()) {
                    downloadAndSaveFile(remoteModule, iconPath, moduleDir)
                }

                val module = Module(
                    id = moduleId,
                    name = props.getProperty("name") ?: moduleId,
                    version = props.getProperty("version") ?: "",
                    versionCode = props.getProperty("versionCode"),
                    author = props.getProperty("author") ?: "",
                    description = props.getProperty("description"),
                    path = moduleDir.absolutePath,
                    repository = remoteModule.repository,
                    html = props.getProperty("html"),
                    install = props.getProperty("install"),
                    permission = props.getProperty("permission"),
                    icon = props.getProperty("icon")
                )

                // If the module declares an install script and it was downloaded, it must run
                // in a Debian terminal before the module counts as installed. If the script
                // is missing (optional file that failed to download) the module counts as
                // installed so it does not disappear from the list.
                if (!installPath.isNullOrBlank() && File(moduleDir, installPath).exists()) {
                    stageToProotDir(moduleDir, moduleId)
                    DownloadModuleResult.NeedsTerminalInstall(module)
                } else {
                    if (!installPath.isNullOrBlank()) {
                        markModuleInstalled(moduleId)
                    }
                    DownloadModuleResult.Downloaded
                }
            } catch (e: Exception) {
                DownloadModuleResult.Failed
            }
        }
    }

    private fun markModuleInstalled(moduleId: String) {
        File(modulesRootPath, moduleId).let { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, ".installed").writeText("installed")
        }
    }

    /** Copies the downloaded module files into the proot guest (elevated when in shizuku/root mode), so the install script can run inside Debian. */
    private suspend fun stageToProotDir(moduleDir: File, moduleId: String) {
        try {
            val targetPath = File(distributionDir(), "opt/modules-box/$moduleId").absolutePath
            val ok = if (isDistroShizukuRoot()) {
                PrivilegedFileOps.copyDirToPrivileged(context, moduleDir, targetPath)
            } else {
                val targetDir = File(targetPath)
                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()
                moduleDir.copyRecursively(targetDir, overwrite = true)
                true
            }
            if (!ok) Log.w(TAG, "Failed to stage module $moduleId into proot dir")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stage module $moduleId into proot dir", e)
        }
    }

   // زيارة رابط badge لتسجيل زيارة للمستودع
	private suspend fun triggerVisitorBadge(remoteModule: RemoteModule) {
	    try {
	        val repoPath = remoteModule.repository.removePrefix("https://github.com/")
	        val badgeUrl = "https://visitor-badge.laobi.icu/badge?page_id=$repoPath"
	        client.get(badgeUrl)
	    } catch (e: Exception) {
	    }
	}
	
    private suspend fun downloadAndSaveFile(remoteModule: RemoteModule, fileName: String, targetDir: File) {
        val bytes = downloadRawBytes(remoteModule.repository, fileName) ?: return
        val targetFile = File(targetDir, fileName)
        try {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { output ->
                output.write(bytes)
            }
        } catch (e: Exception) {
            // لا يعتبر خطأ فادحًا إذا لم يتم العثور على الملف (قد يكون اختياريًا).
        }
    }

    private fun saveContentToFile(content: String, file: File) {
        file.writeText(content)
    }
}
