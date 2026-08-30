package com.yassernull.nullbox

import android.app.Application
import com.yassernull.nullbox.ipc.RishDaemon
import com.yassernull.nullbox.utils.application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {

    companion object {
        /**
         * Application-private tmp directory used by the rish daemon file-based IPC.
         * Matches the `base_dir` compiled into `app/src/main/jni/rish/rish.c`.
         */
        @OptIn(DelicateCoroutinesApi::class)
        fun getTempDir(): File {
            val tmp = File(application!!.filesDir.parentFile, "tmp")
            if (!tmp.exists()) tmp.mkdir()
            return tmp
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this

        // Clean stale rish IPC files from a previous run on a background thread.
        GlobalScope.launch(Dispatchers.IO) {
            val tmp = getTempDir()
            if (tmp.exists() && !tmp.listFiles().isNullOrEmpty()) {
                tmp.deleteRecursively()
            }
            getTempDir() // re-create
        }

        RishDaemon.start(this)
    }
}
