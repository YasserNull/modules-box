package com.yassernull.modulesbox.receivers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import android.widget.Toast
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.data.repository.ModuleRepository
import com.yassernull.modulesbox.core.AppBrowser
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.ui.activities.ModuleWebViewActivity
import com.yassernull.modulesbox.utils.ModuleInstaller
import com.yassernull.modulesbox.utils.RuntimeTmp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModuleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_START_MODULE) {
            val moduleId = intent.getStringExtra(EXTRA_MODULE_ID) ?: return
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RuntimeTmp.prepare(context)
                    val repository = ModuleRepository(context)
                    val modules = repository.getModules()
                    val module = modules.find { it.id == moduleId } ?: return@launch
                    
                    var port = ModuleInstaller.getModulePort(moduleId)
                    var isColdStart = false
                    if (port == null) {
                        isColdStart = true
                        port = ModuleInstaller.startModuleServer(context, module)
                    }
                    
                    if (port != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.module_started), Toast.LENGTH_SHORT).show()
                        }
                        if (isColdStart) {
                            var attempts = 0
                            while (attempts < 15 && !ModuleInstaller.isPortOpen(port)) {
                                kotlinx.coroutines.delay(200)
                                attempts++
                            }
                            kotlinx.coroutines.delay(300)
                        }
                        val page = module.html?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
                        val siteUrl = "http://localhost:$port$page"
                        val defaultBrowser = AppPreferences(context).getDefaultBrowserSync()
                        if (defaultBrowser == AppBrowser.MODULES_BOX) {
                            ModuleWebViewActivity.launch(context, module.path, siteUrl, module.name)
                        } else {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.module_start_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_START_MODULE = "com.yassernull.modulesbox.action.START_MODULE"
        const val EXTRA_MODULE_ID = "module_id"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val moduleId = prefs.getString("widget_${appWidgetId}_id", null)
            val moduleName = prefs.getString("widget_${appWidgetId}_name", "") ?: ""
            val moduleIconPath = prefs.getString("widget_${appWidgetId}_icon", null)
            val modulePath = prefs.getString("widget_${appWidgetId}_path", null)
            val showName = prefs.getBoolean("widget_${appWidgetId}_show_name", false)

            if (moduleId == null) return

            val views = RemoteViews(context.packageName, R.layout.widget_module)

            var bitmap: Bitmap? = null
            if (moduleIconPath != null && modulePath != null) {
                val iconFile = File(modulePath, moduleIconPath)
                if (iconFile.exists()) {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(iconFile.absolutePath, options)
                    var inSampleSize = 1
                    val target = 192
                    if (options.outHeight > target || options.outWidth > target) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / inSampleSize >= target && halfWidth / inSampleSize >= target) {
                            inSampleSize *= 2
                        }
                    }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    // prevent hardware bitmap configuration which is buggy with widgets
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888 
                    bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                }
            }

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_icon, roundBitmap(bitmap))
            } else {
                views.setImageViewBitmap(R.id.widget_icon, generateLetterBitmap(moduleName))
            }

            if (showName) {
                views.setViewVisibility(R.id.widget_name, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_name, moduleName)
            } else {
                views.setViewVisibility(R.id.widget_name, android.view.View.GONE)
            }

            val intent = Intent(context, ModuleWidgetProvider::class.java).apply {
                action = ACTION_START_MODULE
                data = android.net.Uri.parse("widget://$appWidgetId")
                putExtra(EXTRA_MODULE_ID, moduleId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_name, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun roundBitmap(bitmap: Bitmap): Bitmap {
            val targetSize = 192
            val size = Math.min(bitmap.width, bitmap.height)
            val scale = if (size > targetSize) targetSize.toFloat() / size else 1f
            val scaledSize = (size * scale).toInt()
            
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            
            val output = Bitmap.createBitmap(scaledSize, scaledSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, scaledSize.toFloat(), scaledSize.toFloat())
            val cornerRadius = scaledSize / 6f
            
            // Draw rounded rect mask
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            
            // Draw bitmap masked
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            val dx = (scaledSize - scaledBitmap.width) / 2f
            val dy = (scaledSize - scaledBitmap.height) / 2f
            canvas.drawBitmap(scaledBitmap, dx, dy, paint)
            
            return output
        }

        private fun generateLetterBitmap(name: String): Bitmap {
            val size = 192
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#4A6572") // Primary container color fallback
            }
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            val cornerRadius = size / 6f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            val textPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = size / 2f
                textAlign = Paint.Align.CENTER
            }
            val letter = if (name.isNotBlank()) name.take(1).uppercase() else "M"
            val textBaseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(letter, rect.centerX(), textBaseline, textPaint)

            return output
        }
    }
}
