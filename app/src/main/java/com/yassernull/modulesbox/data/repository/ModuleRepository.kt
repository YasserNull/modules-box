package com.yassernull.modulesbox.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.utils.PrivilegedFileOps
import com.yassernull.modulesbox.utils.distributionDir
import com.yassernull.modulesbox.utils.isDistroShizukuRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipInputStream

/**
 * Result of extracting a module ZIP.
 *  - [Installed]: the module has no install script and is ready to use.
 *  - [NeedsTerminalInstall]: the module has an install script that must be run in a
 *    Alpine terminal before it counts as installed.
 *  - [Failed]: the archive could not be processed.
 */
sealed class ZipInstallResult {
    object Installed : ZipInstallResult()
    data class NeedsTerminalInstall(val module: Module) : ZipInstallResult()
    data class Failed(val reason: String) : ZipInstallResult()
}

class ModuleRepository(
    private val context: Context
) {

    private val TAG = "ModuleRepository"

    private val modulesRootPath by lazy { File(context.filesDir, "modules").absolutePath }

    /** Marker file written once a module's install script completes successfully. */
    private fun installedMarker(moduleDir: File): File = File(moduleDir, ".installed")

    /** Marks a module as fully installed (its install script completed successfully). */
    fun markModuleInstalled(moduleId: String) {
        val dir = File(modulesRootPath, moduleId)
        dir.mkdirs()
        installedMarker(dir).writeText("installed")
    }

    /** Removes the installed marker (e.g. after a failed install script). */
    fun markModuleInstallFailed(moduleId: String) {
        installedMarker(File(modulesRootPath, moduleId)).delete()
    }

    suspend fun getModules(): List<Module> {
        return withContext(Dispatchers.IO) {
            val modulesList = mutableListOf<Module>()
            val modulesDir = File(modulesRootPath)
            if (!modulesDir.exists()) modulesDir.mkdirs()

            modulesDir.listFiles()?.forEach { moduleFolder ->
                if (moduleFolder.isDirectory) {
                    val modulePropFile = File(moduleFolder, "module.prop")
                    val module = readModuleProp(modulePropFile) ?: return@forEach
                    // A module that declares an install script only counts as installed after
                    // that script has completed successfully (marker written by the terminal).
                    // Modules without an install script are installed as soon as their files
                    // are in place.
                    val needsMarker = !module.install.isNullOrBlank()
                    if (!needsMarker || installedMarker(moduleFolder).exists()) {
                        modulesList.add(module)
                    }
                }
            }
            modulesList
        }
    }

    suspend fun installFromZip(zipUri: Uri, context: Context): ZipInstallResult {
        return withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "unzip_temp_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var entry = zipInputStream.nextEntry
                        while (entry != null) {
                            val newFile = File(tempDir, entry.name)
                            if (entry.isDirectory) newFile.mkdirs() else {
                                newFile.parentFile?.mkdirs()
                                FileOutputStream(newFile).use { fos -> zipInputStream.copyTo(fos) }
                            }
                            zipInputStream.closeEntry()
                            entry = zipInputStream.nextEntry
                        }
                    }
                } ?: return@withContext ZipInstallResult.Failed("تعذر فتح ملف ZIP.")

                val modulePropFile = findModuleProp(tempDir) ?: throw IOException("لم يتم العثور على module.prop.")
                val moduleContentDir = modulePropFile.parentFile ?: throw IOException("هيكل الوحدة غير صالح.")
                val props = Properties().apply { load(FileInputStream(modulePropFile)) }
                val moduleId = props.getProperty("id") ?: throw IOException("معرف الوحدة غير موجود.")

                val finalModuleDir = File(modulesRootPath, moduleId)
                if (finalModuleDir.exists()) finalModuleDir.deleteRecursively()
                finalModuleDir.mkdirs()
                moduleContentDir.copyRecursively(finalModuleDir, overwrite = true)

                // Best-effort staging into the proot guest (needed to RUN the module
                // later). The terminal install re-stages fresh before running the
                // script, so a hiccup here must not block the install flow.
                val prootTargetPath = File(distributionDir(), "opt/modules-box/$moduleId").absolutePath
                val staged = if (isDistroShizukuRoot()) {
                    PrivilegedFileOps.copyDirToPrivileged(context, moduleContentDir, prootTargetPath)
                } else {
                    try {
                        val prootTargetDir = File(prootTargetPath)
                        if (prootTargetDir.exists()) prootTargetDir.deleteRecursively()
                        prootTargetDir.mkdirs()
                        moduleContentDir.copyRecursively(prootTargetDir, overwrite = true)
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stage module $moduleId into proot dir", e)
                        false
                    }
                }
                if (!staged) Log.w(TAG, "proot staging deferred to terminal install for $moduleId")

                val module = readModuleProp(File(finalModuleDir, "module.prop"))
                    ?: throw IOException("module.prop غير صالح.")

                // If the module declares an install script and it exists, it must run in an
                // Alpine terminal before the module counts as installed.
                val installPath = module.install
                if (!installPath.isNullOrBlank() && File(module.path, installPath).exists()) {
                    ZipInstallResult.NeedsTerminalInstall(module)
                } else {
                    installedMarker(finalModuleDir).writeText("installed")
                    ZipInstallResult.Installed
                }
            } catch (e: Exception) {
                ZipInstallResult.Failed(e.message ?: e.toString())
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private fun findModuleProp(directory: File): File? {
        directory.walkTopDown().forEach { file ->
            if (file.isFile && file.name == "module.prop") return file
        }
        return null
    }

    suspend fun uninstallModule(module: Module): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                File(module.path).deleteRecursively()
                val prootDir = File(distributionDir(), "opt/modules-box/${module.id}")
                if (isDistroShizukuRoot()) {
                    PrivilegedFileOps.deletePrivileged(context, prootDir)
                } else if (prootDir.exists()) {
                    prootDir.deleteRecursively()
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun readModuleProp(modulePropFile: File): Module? {
        if (!modulePropFile.exists()) return null
        val props = Properties()
        return try {
            FileInputStream(modulePropFile).use { props.load(it) }
            Module(
                id = props.getProperty("id") ?: return null,
                name = props.getProperty("name") ?: return null,
                version = props.getProperty("version") ?: "",
                versionCode = props.getProperty("versionCode"),
                author = props.getProperty("author") ?: "",
                description = props.getProperty("description"),
                path = modulePropFile.parentFile?.absolutePath ?: "",
                repository = props.getProperty("repository"),
                html = props.getProperty("html"),
                install = props.getProperty("install"),
                permission = props.getProperty("permission"),
                icon = props.getProperty("icon")
            )
        } catch (e: Exception) {
            null
        }
    }
}
