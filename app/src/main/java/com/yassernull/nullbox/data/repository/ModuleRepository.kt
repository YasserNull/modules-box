package com.yassernull.nullbox.data.repository

import android.content.Context
import android.net.Uri
import com.yassernull.nullbox.data.model.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipInputStream

class ModuleRepository(
    private val context: Context
) {

    private val modulesRootPath by lazy { File(context.filesDir, "modules").absolutePath }

    suspend fun getModules(): List<Module> {
        return withContext(Dispatchers.IO) {
            val modulesList = mutableListOf<Module>()
            val modulesDir = File(modulesRootPath)
            if (!modulesDir.exists()) modulesDir.mkdirs()

            modulesDir.listFiles()?.forEach { moduleFolder ->
                if (moduleFolder.isDirectory) {
                    val modulePropFile = File(moduleFolder, "module.prop")
                    readModuleProp(modulePropFile)?.let { modulesList.add(it) }
                }
            }
            modulesList
        }
    }

    suspend fun installFromZip(zipUri: Uri, context: Context): Boolean {
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
                } ?: return@withContext false

                val modulePropFile = findModuleProp(tempDir) ?: throw IOException("لم يتم العثور على module.prop.")
                val moduleContentDir = modulePropFile.parentFile ?: throw IOException("هيكل الوحدة غير صالح.")
                val props = Properties().apply { load(FileInputStream(modulePropFile)) }
                val moduleId = props.getProperty("id") ?: throw IOException("معرف الوحدة غير موجود.")

                val finalModuleDir = File(modulesRootPath, moduleId)
                if (finalModuleDir.exists()) finalModuleDir.deleteRecursively()
                finalModuleDir.mkdirs()
                moduleContentDir.copyRecursively(finalModuleDir, overwrite = true)
                true
            } catch (e: Exception) {
                false
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
