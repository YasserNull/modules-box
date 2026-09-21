package com.yassernull.modulesbox.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * Manages port allocation for web server modules.
 * Generates random ports and tracks used ports to avoid conflicts.
 */
object PortManager {
    private const val PREFS_NAME = "port_manager"
    private const val KEY_USED_PORTS = "used_ports"
    private const val MIN_PORT = 1024
    private const val MAX_PORT = 65535
    private const val MAX_RETRIES = 100

    private val secureRandom = SecureRandom()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get all currently used ports.
     */
    fun getUsedPorts(context: Context): Set<Int> {
        val prefs = getPrefs(context)
        val portString = prefs.getString(KEY_USED_PORTS, "") ?: ""
        if (portString.isBlank()) return emptySet()
        return portString.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /**
     * Generate a random available port.
     * Returns null if no available port is found after MAX_RETRIES.
     */
    fun generateAvailablePort(context: Context): Int? {
        val usedPorts = getUsedPorts(context)
        
        repeat(MAX_RETRIES) {
            val port = generateRandomPort()
            if (port !in usedPorts) {
                return port
            }
        }
        return null
    }

    /**
     * Mark a port as used.
     */
    fun acquirePort(context: Context, port: Int): Boolean {
        val usedPorts = getUsedPorts(context).toMutableSet()
        if (port in usedPorts) return false
        usedPorts.add(port)
        saveUsedPorts(context, usedPorts)
        return true
    }

    /**
     * Release a port so it can be used again.
     */
    fun releasePort(context: Context, port: Int) {
        val usedPorts = getUsedPorts(context).toMutableSet()
        usedPorts.remove(port)
        saveUsedPorts(context, usedPorts)
    }

    /**
     * Check if a port is available.
     */
    fun isPortAvailable(context: Context, port: Int): Boolean {
        val usedPorts = getUsedPorts(context)
        return port !in usedPorts
    }

    private fun generateRandomPort(): Int {
        return MIN_PORT + secureRandom.nextInt(MAX_PORT - MIN_PORT + 1)
    }

    private fun saveUsedPorts(context: Context, ports: Set<Int>) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_USED_PORTS, ports.joinToString(",")).apply()
    }
}
