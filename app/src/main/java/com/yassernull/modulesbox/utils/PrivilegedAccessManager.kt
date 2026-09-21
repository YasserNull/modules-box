package com.yassernull.modulesbox.utils

import android.app.Activity
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

object PrivilegedAccessManager {
    private const val SHIZUKU_REQUEST_CODE = 1001

    fun hasShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun hasRootPermission(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (e: Throwable) {
            false
        }
    }

    fun ensureShizukuPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!Shizuku.pingBinder()) {
            activity.runOnUiThread { onResult(false) }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            activity.runOnUiThread { onResult(true) }
            return
        }
        lateinit var listener: Shizuku.OnRequestPermissionResultListener
        listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                Shizuku.removeRequestPermissionResultListener(listener)
                activity.runOnUiThread { onResult(grantResult == PackageManager.PERMISSION_GRANTED) }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    fun ensureRootPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        val shell = Shell.getShell()
        val granted = shell.isRoot
        activity.runOnUiThread { onResult(granted) }
    }
}
