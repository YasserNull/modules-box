package com.yassernull.modulesbox.receivers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.ui.activities.ModuleLauncherActivity
import java.io.File

class ModuleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {

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
                    try {
                        bitmap = BitmapFactory.decodeFile(iconFile.absolutePath, options)
                        android.util.Log.i("ModuleWidgetProvider", "widget $appWidgetId icon decoded: ${iconFile.absolutePath} -> ${bitmap?.width}x${bitmap?.height}")
                    } catch (e: Exception) {
                        android.util.Log.e("ModuleWidgetProvider", "widget $appWidgetId icon decode failed for ${iconFile.absolutePath}", e)
                        bitmap = null
                    }
                } else {
                    android.util.Log.w("ModuleWidgetProvider", "widget $appWidgetId icon file missing: ${iconFile.absolutePath}")
                }
            } else {
                android.util.Log.w("ModuleWidgetProvider", "widget $appWidgetId missing icon/path: icon=$moduleIconPath path=$modulePath")
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

            val intent = ModuleLauncherActivity.intentForModule(context, moduleId, appWidgetId)
            val pendingIntent = PendingIntent.getActivity(
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
