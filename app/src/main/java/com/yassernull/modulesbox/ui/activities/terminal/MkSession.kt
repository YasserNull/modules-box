package com.yassernull.modulesbox.ui.activities.terminal

import android.app.Activity
import android.content.Context
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.yassernull.modulesbox.App.Companion.getTempDir
import com.yassernull.modulesbox.BuildConfig
import com.yassernull.modulesbox.utils.PrivilegedFileOps
import com.yassernull.modulesbox.utils.child
import com.yassernull.modulesbox.utils.distributionHomeDir
import com.yassernull.modulesbox.utils.localBinDir
import com.yassernull.modulesbox.utils.localLibDir
import java.io.File

object MkSession {
    private const val TAG = "MkSession"

    fun buildAndroidEnv(context: Context, sessionId: String, workingMode: Int? = null): Array<String> {
        with(context) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val isDistro = workingMode == WorkingMode.DISTRIBUTION ||
                workingMode == WorkingMode.DISTRIBUTION_ROOT ||
                workingMode == WorkingMode.DISTRIBUTION_SHIZUKU
            // Android privileged sessions (root/shizuku shell) keep their binaries under /data/local/tmp/modules-box.
            val isAndroidPrivileged = workingMode == WorkingMode.SHIZUKU || workingMode == WorkingMode.ROOT
            // Distribution-shizuku runs as the shell user, which cannot read app data,
            // so everything must live under /data/local/tmp/modules-box.
            val isDistroShizuku = workingMode == WorkingMode.DISTRIBUTION_SHIZUKU

            // Distro modes use the permission-aware local dirs (app-private by default,
            // /data/local/tmp/modules-box when the shizuku/root permission is selected).
            val binDir = when {
                isDistro -> localBinDir().absolutePath
                isAndroidPrivileged -> "${PrivilegedFileOps.PRIVILEGED_BASE}/bin"
                else -> localBinDir().absolutePath
            }
            val libDir = when {
                isDistro -> localLibDir().absolutePath
                isAndroidPrivileged -> "${PrivilegedFileOps.PRIVILEGED_BASE}/lib"
                else -> localLibDir().absolutePath
            }
            val tmpDir = if (isAndroidPrivileged || isDistroShizuku) "/data/local/tmp" else getTempDir().absolutePath
            val prootTmpDir = when {
                isDistroShizuku -> "${PrivilegedFileOps.PRIVILEGED_BASE}/.proot"
                isAndroidPrivileged -> "${PrivilegedFileOps.PRIVILEGED_BASE}/.proot"
                else -> getTempDir().child(sessionId).also { if (!it.exists()) it.mkdirs() }.absolutePath
            }
            // init-host computes local = $PREFIX/local; for distro modes derive it from
            // the active bin dir so the shell/root users see the correct rootfs location.
            val prefix = if (isDistro) {
                localBinDir().parentFile?.parentFile?.path ?: filesDir.parentFile!!.path
            } else {
                filesDir.parentFile!!.path
            }

            val androidPaths = "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/apex/com.android.virt/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/sbin"
            val pathValue = if (isDistro) {
                "${System.getenv("PATH")}:/sbin:$binDir:$androidPaths"
            } else {
                "${System.getenv("PATH")}:/sbin:$binDir"
            }

            val env = mutableListOf(
                "PATH=$pathValue",
                "HOME=/sdcard",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=$binDir",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=$prefix",
                "LD_LIBRARY_PATH=$libDir",
                "LINKER=${linkerPath()}",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
                "PROOT_TMP_DIR=$prootTmpDir",
                "TMPDIR=$tmpDir"
            )

            // The proot loaders are used as ELF interpreters, so the kernel requires them
            // to be executable. The copies under the privileged lib dir lose their exec bit
            // during the /sdcard staging copy (chmod a+rX cannot re-add it), which makes
            // proot fail with "execve: Permission denied". The app's nativeLibraryDir is
            // world-executable and readable by the shell user (proven by the Android-shizuku
            // session), so always point the loaders there regardless of the working mode.
            val loaderDir = applicationInfo.nativeLibraryDir

            if (File(loaderDir).child("libproot-loader32.so").exists()) {
                env.add("PROOT_LOADER32=$loaderDir/libproot-loader32.so")
            }

            if (File(loaderDir).child("libproot-loader.so").exists()) {
                env.add("PROOT_LOADER=$loaderDir/libproot-loader.so")
            }

            env.addAll(envVariables.map { "${it.key}=${it.value}" })
            return env.toTypedArray()
        }
    }

    /**
     * Creates a distribution (proot) session. When [installCommand] is non-null it is
     * appended to the `init-host proot` invocation as an extra argument, which makes
     * init-host run `sh -c '<command>'` inside the guest NON-interactively — no shell
     * prompt and no echo, so the terminal shows only the command's output. Used for
     * module install scripts. Normal sessions pass null → interactive shell.
     */
    fun createSession(
        activity: Activity,
        sessionClient: TerminalSessionClient,
        sessionId: String,
        workingMode: Int,
        installCommand: String? = null
    ): TerminalSession {
        with(activity) {
            val workingDir = distributionHomeDir().path
            val initFile = localBinDir().child("init-host")
            val env = buildAndroidEnv(activity, sessionId, workingMode).toMutableList()
            val linker = linkerPath()

            // init-host supports `init-host proot '<command>'` (argv[2]+) → runs the
            // command via /bin/sh inside the guest. The command must not contain single
            // quotes (init-host wraps it in '...'); module paths never contain them.
            fun prootCmd() = if (installCommand != null) {
                "$linker ${initFile.absolutePath} proot '$installCommand'"
            } else {
                "$linker ${initFile.absolutePath} proot"
            }

            val args: Array<String>
            val shell = when (workingMode) {
                WorkingMode.DISTRIBUTION -> {
                    args = arrayOf("/system/bin/sh", "-c", prootCmd())
                    "/system/bin/sh"
                }

                WorkingMode.DISTRIBUTION_ROOT -> {
                    args = arrayOf("su", "-c", prootCmd())
                    "su"
                }

                WorkingMode.DISTRIBUTION_SHIZUKU -> {
                    args = arrayOf("/system/bin/sh", "-c", prootCmd())
                    "/system/bin/sh"
                }

                WorkingMode.ANDROID -> {
                    args = arrayOf("/system/bin/sh")
                    "/system/bin/sh"
                }

                WorkingMode.ROOT -> {
                    args = arrayOf("su")
                    "su"
                }

                WorkingMode.SHIZUKU -> {
                    args = arrayOf("/system/bin/sh")
                    "/system/bin/sh"
                }

                else -> {
                    args = arrayOf("/system/bin/sh")
                    "/system/bin/sh"
                }
            }

            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient
            )
        }
    }

    private fun linkerPath(): String {
        return if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
    }
}
