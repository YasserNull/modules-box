package com.yassernull.modulesbox.data.repository

import android.content.Context
import android.util.Log
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.model.RemoteModule
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    /**
     * [reason] is carried instead of dropped: the common cause is a store entry whose
     * release was never published, which the user must be told about rather than shown
     * as a row that silently does nothing.
     */
    data class Failed(val reason: String) : DownloadModuleResult()
}

// مستودع لإدارة التفاعل مع المستودع عن بعد (جلب القائمة وتنزيل الوحدات).
//
// الفكرة: ملف repository.json واحد في مستودع مركزي هو المصدر الوحيد لقائمة المتجر —
// كل عنصر يحدد الوحدة ونسختها (version) ومستودعها. التنزيل لا يلمس محتويات المستودع
// أبداً: ينزّل أرشيف النسخة (release) المنشور تحت الوسم المذكور في الفهرس، وهو نفس
// الأرشيف الذي ينزّله المستخدم لو نزّله بنفسه، ثم يمرّره على مسار التثبيت المشترك.
class RepoRepository(private val context: Context) {

    private val client = HttpClient(Android)

    /** Extract/unpack/install logic, shared with the "install from storage" flow. */
    private val moduleRepository by lazy { ModuleRepository(context) }

    companion object {
        private const val TAG = "RepoRepository"

        /**
         * The store index: one JSON array describing every module and the version to fetch.
         * Served from raw.githubusercontent.com, so listing costs one request and is not
         * subject to the api.github.com rate limit that a per-repo listing would hit.
         */
        const val REPOSITORY_INDEX_URL =
            "https://raw.githubusercontent.com/modules-box-repo/repository/refs/heads/main/repository.json"

        /** Icon fetches run in small batches: one per module is 20+ requests. */
        private const val ICON_CONCURRENCY = 4
    }

    // جلب قائمة المتجر من ملف repository.json المركزي.
    suspend fun getRepoModules(): RepoFetchResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(REPOSITORY_INDEX_URL)
                if (!response.status.isSuccess()) {
                    Log.w(TAG, "index fetch failed: ${response.status}")
                    return@withContext RepoFetchResult(errorMessage = "repo_load_failed")
                }
                val body = response.bodyAsText()
                val array = JSONArray(body)
                val modules = (0 until array.length()).mapNotNull { parseRemoteModule(array.optJSONObject(it)) }
                Log.i(TAG, "getRepoModules: ${modules.size} module(s) from index")
                RepoFetchResult(modules = modules)
            } catch (e: Exception) {
                Log.w(TAG, "getRepoModules failed", e)
                RepoFetchResult(errorMessage = "repo_load_failed")
            }
        }
    }

    /**
     * Maps one repository.json entry.
     *
     * The index is the store's only source of truth, so a row missing the fields needed to
     * download it (repository, version) is dropped instead of shown and failing on tap.
     */
    private fun parseRemoteModule(obj: JSONObject?): RemoteModule? {
        if (obj == null) return null
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val repository = obj.optString("repository").trim().trimEnd('/')
            .takeIf { it.startsWith("https://github.com/") } ?: return null
        val version = obj.optString("version").takeIf { it.isNotBlank() } ?: return null
        return RemoteModule(
            id = id,
            name = obj.optString("name").ifBlank { id },
            description = obj.optString("description"),
            author = obj.optString("author"),
            version = version,
            versionCode = jsonVersionCode(obj),
            repository = repository,
            icon = obj.optString("icon").trim().ifBlank { null },
            // HEAD resolves to the repository's default branch, so this works for repos
            // that have not renamed main to master.
            readmeUrl = "$repository/blob/HEAD/README.md"
        )
    }

    /** versionCode is a number in the index but a string in module.prop. */
    private fun jsonVersionCode(obj: JSONObject): String = when (val raw = obj.opt("versionCode")) {
        null -> ""
        is Number -> raw.toInt().toString()
        else -> raw.toString().trim()
    }

    /**
     * Downloads each module's icon to the cache and reports it through [onIconLoaded] as it
     * arrives.
     *
     * Deliberately separate from [getRepoModules] and not awaited by it: the store has 20+
     * icons, and making the list wait on all of them turns a slow connection into an empty
     * screen. The list is shown first with letter avatars and the pictures fill in.
     * [onIconLoaded] is called from a background dispatcher, so callers must hop back to
     * their own scope before touching UI state.
     */
    suspend fun loadIcons(
        modules: List<RemoteModule>,
        onIconLoaded: (moduleId: String, localPath: String) -> Unit
    ) {
        val pending = modules.filter { !it.icon.isNullOrBlank() }
        if (pending.isEmpty()) return
        val dir = File(context.cacheDir, "repo_icons").apply { mkdirs() }
        withContext(Dispatchers.IO) {
            coroutineScope {
                pending.chunked(ICON_CONCURRENCY).forEach { batch ->
                    batch.map { module ->
                        async {
                            downloadIcon(module, dir)?.let { path -> onIconLoaded(module.id, path) }
                        }
                    }.awaitAll()
                }
            }
        }
        Log.i(TAG, "loadIcons: ${pending.size} icon(s) processed")
    }

    /**
     * Fetches one icon from the module repository, or null if it is unavailable.
     *
     * Ref-uses HEAD instead of guessing main/master: the index does not carry a branch, and
     * HEAD always points at the repository's default branch. A cached copy is reused, so
     * re-opening the store does not re-download every icon.
     */
    private suspend fun downloadIcon(module: RemoteModule, cacheDir: File): String? {
        val iconFile = module.icon ?: return null
        val fileName = iconFile.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val target = File(cacheDir, "${module.id}_$fileName")
        if (target.length() > 0L) return target.absolutePath
        return try {
            val url = "https://raw.githubusercontent.com/${module.repository.removePrefix("https://github.com/")}/HEAD/$iconFile"
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                Log.i(TAG, "icon unavailable for ${module.id}: $url (${response.status})")
                return null
            }
            // Write to a temp name first: a half-written file would look like a valid cache
            // hit on the next launch. Streamed, since a few module icons are 200KB+ and the
            // store fetches twenty of them in a row.
            val tmp = File(target.parentFile, "${target.name}.part")
            tmp.outputStream().use { out ->
                response.bodyAsChannel().toInputStream().use { input -> input.copyTo(out) }
            }
            if (tmp.length() == 0L) {
                tmp.delete()
                return null
            }
            tmp.renameTo(target)
            Log.i(TAG, "icon cached for ${module.id} (${target.length()} bytes)")
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "icon download failed for ${module.id}", e)
            null
        }
    }

    // تنزيل نسخة الوحدة المنشورة (release) ثم تثبيتها.
    suspend fun downloadModule(remoteModule: RemoteModule): DownloadModuleResult {
        return withContext(Dispatchers.IO) {
            triggerVisitorBadge(remoteModule)
            val archive = downloadReleaseArchive(remoteModule)
                ?: return@withContext DownloadModuleResult.Failed(
                    "no release asset for ${remoteModule.id} ${remoteModule.version}"
                )
            try {
                when (val result = moduleRepository.installFromArchive(archive)) {
                    ZipInstallResult.Installed -> DownloadModuleResult.Downloaded
                    is ZipInstallResult.NeedsTerminalInstall ->
                        DownloadModuleResult.NeedsTerminalInstall(result.module)
                    is ZipInstallResult.Failed -> DownloadModuleResult.Failed(result.reason)
                }
            } finally {
                archive.delete()
            }
        }
    }

    /**
     * Downloads the module's published release asset for the tag named by the index version.
     *
     * The publishing convention is `<tag>.zip`. Releases published before that convention
     * carry `<moduleId>.zip` instead, so the second candidate keeps those installs working
     * until every release is renamed; both live under the same tag, so a 404 on the first
     * just falls through to the next.
     */
    private suspend fun downloadReleaseArchive(remoteModule: RemoteModule): File? {
        val tag = remoteModule.version.encodeURLPathPart()
        val base = "${remoteModule.repository}/releases/download/$tag"
        val candidates = listOf(
            "$base/$tag.zip",
            "$base/${remoteModule.id.encodeURLPathPart()}.zip"
        )
        val target = File(context.cacheDir, "release_${remoteModule.id}.zip")
        for (candidate in candidates) {
            try {
                val response = client.get(candidate)
                if (!response.status.isSuccess()) {
                    Log.i(TAG, "release asset unavailable: $candidate (${response.status})")
                    continue
                }
                // Streamed to disk: a module archive can be large and holding it in memory
                // on a low-end device is an easy way to an OOM mid-install.
                target.outputStream().use { out ->
                    response.bodyAsChannel().toInputStream().use { input -> input.copyTo(out) }
                }
                if (target.length() == 0L) {
                    Log.w(TAG, "empty release asset: $candidate")
                    target.delete()
                    continue
                }
                Log.i(TAG, "downloaded ${remoteModule.id} ${remoteModule.version} from $candidate (${target.length()} bytes)")
                return target
            } catch (e: Exception) {
                Log.w(TAG, "release download failed: $candidate", e)
            }
        }
        return null
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
}
