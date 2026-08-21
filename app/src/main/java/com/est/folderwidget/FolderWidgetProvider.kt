package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.RemoteViews

class FolderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    /**
     * เรียกอัตโนมัติทุกครั้งที่ผู้ใช้ "ลากปรับขนาด" widget บนหน้าโฮม
     * Android ส่งขนาดใหม่ (dp) มาให้ผ่าน newOptions -> เราคำนวณว่าควร
     * ใช้เลย์เอาต์ไหน (small/medium/large) แล้วอัปเดต widget ใหม่ทันที
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            PrefsHelper.deleteWidgetData(context, id)
        }
    }

    companion object {

        // id ของ ImageView แต่ละช่อง ต้องประกาศตรงกับ @+id ใน widget_folder_small/medium/large.xml
        // (id ชื่อเดียวกันใช้ร่วมกันได้ข้ามไฟล์เลย์เอาต์ เพราะ Android รวม id ตามชื่อ ไม่ใช่ตามไฟล์)
        private val SMALL_ICON_IDS = intArrayOf(
            R.id.mini_icon_1, R.id.mini_icon_2, R.id.mini_icon_3, R.id.mini_icon_4
        )
        private val MEDIUM_ICON_IDS = intArrayOf(
            R.id.mini_icon_1, R.id.mini_icon_2, R.id.mini_icon_3,
            R.id.mini_icon_4, R.id.mini_icon_5, R.id.mini_icon_6,
            R.id.mini_icon_7, R.id.mini_icon_8, R.id.mini_icon_9
        )
        private val LARGE_ICON_IDS = intArrayOf(
            R.id.mini_icon_1, R.id.mini_icon_2, R.id.mini_icon_3, R.id.mini_icon_4,
            R.id.mini_icon_5, R.id.mini_icon_6, R.id.mini_icon_7, R.id.mini_icon_8,
            R.id.mini_icon_9, R.id.mini_icon_10, R.id.mini_icon_11, R.id.mini_icon_12,
            R.id.mini_icon_13, R.id.mini_icon_14, R.id.mini_icon_15, R.id.mini_icon_16
        )

        /**
         * เลือกเลย์เอาต์ตามขนาดจริงที่ผู้ใช้ลากไว้ (หน่วย dp ที่ Android ส่งมาให้)
         * เกณฑ์: ยิ่งกว้าง/สูงมาก ยิ่งใช้กริดที่มีไอคอนเยอะขึ้น เหมือนโฟลเดอร์ขยายเห็นแอพมากขึ้น
         * ปรับตัวเลข threshold (140 / 220) ได้ตามที่ลองแล้วรู้สึกพอดีตาที่สุด
         */
        private fun pickLayout(minWidthDp: Int, minHeightDp: Int): Triple<Int, IntArray, Int> {
            val size = minOf(minWidthDp, minHeightDp)
            return when {
                size >= 220 -> Triple(R.layout.widget_folder_large, LARGE_ICON_IDS, 16)
                size >= 140 -> Triple(R.layout.widget_folder_medium, MEDIUM_ICON_IDS, 9)
                else -> Triple(R.layout.widget_folder_small, SMALL_ICON_IDS, 4)
            }
        }

        /** เรียกจาก config activity ตอนกดบันทึกครั้งแรก (ดึงขนาดปัจจุบันของ widget เองจาก AppWidgetManager) */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            updateWidget(context, appWidgetManager, widgetId, options)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            options: Bundle?
        ) {
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 80) ?: 80
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80) ?: 80

            val (layoutRes, iconIds, maxIcons) = pickLayout(minWidthDp, minHeightDp)
            val views = RemoteViews(context.packageName, layoutRes)

            val packages = PrefsHelper.getPackages(context, widgetId)

            for (i in iconIds.indices) {
                val iconViewId = iconIds[i]
                if (i < packages.size && i < maxIcons) {
                    val icon = AppRepository.loadIcon(context, packages[i])
                    if (icon != null) {
                        views.setImageViewBitmap(iconViewId, drawableToBitmap(icon))
                        views.setViewVisibility(iconViewId, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(iconViewId, android.view.View.INVISIBLE)
                    }
                } else {
                    views.setViewVisibility(iconViewId, android.view.View.INVISIBLE)
                }
            }

            val openPopupIntent = Intent(context, FolderPopupActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            // ฝัง custom animation ลงใน PendingIntent ตรงๆ (ไม่ใช่แค่ตั้งใน theme)
            // เพื่อบังคับให้เล่น scale+fade ของเราแทน animation เปิดแอพมาตรฐานของระบบ/launcher
            val animOptions = android.app.ActivityOptions.makeCustomAnimation(
                context, R.anim.popup_enter, R.anim.popup_exit
            )
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                widgetId,
                openPopupIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                animOptions.toBundle()
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable) return drawable.bitmap
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }
}
