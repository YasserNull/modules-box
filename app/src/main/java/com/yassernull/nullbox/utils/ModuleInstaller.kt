package com.yassernull.nullbox.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yassernull.nullbox.data.model.Module
import com.yassernull.nullbox.ui.activities.TerminalActivity
import java.io.File

/**
 * Handles module installation by running install scripts in Alpine terminal sessions.
 */
object ModuleInstaller {
    private const val TAG = "ModuleInstaller"

    /**
     * Check if a module has an install script.
     */
    fun hasInstallScript(module: Module): Boolean {
        return !module.install.isNullOrBlank()
    }

    /**
     * Get the install script file for a module.
     */
    fun getInstallScriptFile(module: Module): File? {
        val installPath = module.install ?: return null
        val scriptFile = File(module.path, installPath)
        return if (scriptFile.exists()) scriptFile else null
    }

    /**
     * Run the install script for a module in a new Alpine terminal session.
     * Returns true if the script was started successfully.
     */
    fun runInstallScript(context: Context, module: Module): Boolean {
        val scriptFile = getInstallScriptFile(module) ?: run {
            Log.w(TAG, "Install script not found for module: ${module.id}")
            return false
        }

        return try {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra(TerminalActivity.EXTRA_INSTALL_MODULE_ID, module.id)
                putExtra(TerminalActivity.EXTRA_INSTALL_SCRIPT_PATH, scriptFile.absolutePath)
                putExtra(TerminalActivity.EXTRA_INSTALL_MODULE_NAME, module.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install script for module: ${module.id}", e)
            false
        }
    }

    /**
     * Start a PHP server for a module with HTML file.
     * Runs: php -S localhost:$PORT in the module directory.
     * Returns the port number if started successfully, null otherwise.
     */
    fun startPhpServer(context: Context, module: Module): Int? {
        val port = PortManager.generateAvailablePort(context) ?: run {
            Log.w(TAG, "No available port for module: ${module.id}")
            return null
        }

        if (!PortManager.acquirePort(context, port)) {
            Log.w(TAG, "Failed to acquire port $port for module: ${module.id}")
            return null
        }

        return try {
            val command = "php -S localhost:$port"
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra(TerminalActivity.EXTRA_INSTALL_MODULE_ID, module.id)
                putExtra(TerminalActivity.EXTRA_INSTALL_SCRIPT_PATH, "")
                putExtra(TerminalActivity.EXTRA_INSTALL_COMMAND, command)
                putExtra(TerminalActivity.EXTRA_INSTALL_MODULE_NAME, module.name)
                putExtra(TerminalActivity.EXTRA_INSTALL_WORKING_DIR, module.path)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PHP server for module: ${module.id}", e)
            PortManager.releasePort(context, port)
            null
        }
    }

    /**
     * Stop a PHP server by releasing its port.
     */
    fun stopPhpServer(context: Context, port: Int) {
        PortManager.releasePort(context, port)
    }
}
