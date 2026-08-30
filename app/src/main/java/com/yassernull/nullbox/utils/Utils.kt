package com.yassernull.nullbox.utils

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
fun runOnUiThread(runnable: Runnable) {
    GlobalScope.launch(Dispatchers.Main) {
        runnable.run()
    }
}

fun toast(message: String?) {
    if (message.isNullOrBlank()) return
    runOnUiThread {
        Toast.makeText(application!!, message, Toast.LENGTH_SHORT).show()
    }
}

fun toast(e: Exception? = null) {
    e?.printStackTrace()
    e?.message?.let { toast(it) }
}

suspend fun <T> withIO(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

fun isDarkMode(ctx: Context): Boolean {
    return (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

fun dpToPx(dp: Float, ctx: Context): Int {
    return Math.round(dp * ctx.resources.displayMetrics.density)
}

fun dpToPxFloat(dp: Float, ctx: Context): Float {
    return dp * ctx.resources.displayMetrics.density
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

fun formatProgress(downloaded: Long, total: Long): String {
    if (total <= 0) return ""
    return "${formatBytes(downloaded)}/${formatBytes(total)}"
}
