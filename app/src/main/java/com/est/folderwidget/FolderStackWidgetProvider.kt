package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.RemoteViews

/**
 * วิตเจ็ต "โฟลเดอร์ไม่จำกัด" — ใส่แอพกี่ตัวก็ได้
 * โชว์บนหน้าโฮมแค่ 3 ไอคอนแรกแบบเต็มขนาด + ช่องที่ 4 เป็นภาพคอลลาจ
 * ย่อไอคอนแอพที่เหลือ (ตัวที่ 4 เป็นต้นไป) มาเรียงเป็นกริดเล็กๆ ในช่องเดียว
 * ให้ความรู้สึกเหมือน "โฟลเดอร์ซ้อนแอพเพิ่ม" คล้ายพรีวิวโฟลเดอร์ iOS/MIUI
 */
class FolderStackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            PrefsHelper.deleteWidgetData(context, id)
        }
    }

    companion object {

        private const val STACK_PREVIEW_MAX = 4 // จำนวนแอพที่ยัดลงในคอลลาจช่องที่ 4

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_folder_stack)
            val packages = PrefsHelper.getPackages(context, widgetId)

            // 3 ไอคอนแรกแบบเต็ม
            val topSlots = listOf(R.id.mini_icon_1, R.id.mini_icon_2, R.id.mini_icon_3)
            for (i in topSlots.indices) {
                val viewId = topSlots[i]
                if (i < packages.size) {
                    val icon = AppRepository.loadIcon(context, packages[i])
                    if (icon != null) {
                        views.setImageViewBitmap(viewId, drawableToBitmap(icon))
                        views.setViewVisibility(viewId, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(viewId, android.view.View.INVISIBLE)
                    }
                } else {
                    views.setViewVisibility(viewId, android.view.View.INVISIBLE)
                }
            }

            // ช่องที่ 4: ถ้ามีแอพมากกว่า 3 -> ทำคอลลาจของแอพที่เหลือ
            // ถ้ามีพอดี 4 -> โชว์ไอคอนตัวที่ 4 เดี่ยวๆ ตามปกติ
            // ถ้ามี <=3 -> ซ่อนช่องนี้ไป
            when {
                packages.size > 4 -> {
                    val remaining = packages.subList(3, packages.size)
                        .take(STACK_PREVIEW_MAX)
                        .mapNotNull { AppRepository.loadIcon(context, it) }
                    if (remaining.isNotEmpty()) {
                        views.setImageViewBitmap(R.id.mini_icon_stack, composeStackBitmap(remaining))
                        views.setViewVisibility(R.id.mini_icon_stack, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.mini_icon_stack, android.view.View.INVISIBLE)
                    }
                }
                packages.size == 4 -> {
                    val icon = AppRepository.loadIcon(context, packages[3])
                    if (icon != null) {
                        views.setImageViewBitmap(R.id.mini_icon_stack, drawableToBitmap(icon))
                        views.setViewVisibility(R.id.mini_icon_stack, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.mini_icon_stack, android.view.View.INVISIBLE)
                    }
                }
                else -> {
                    views.setViewVisibility(R.id.mini_icon_stack, android.view.View.INVISIBLE)
                }
            }

            val openPopupIntent = Intent(context, FolderPopupActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
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

        /**
         * ประกอบไอคอนของแอพที่เหลือ (สูงสุด 4 ตัว) ลงในบิตแมปเดียว
         * จัดเป็นกริด 2x2 ย่อส่วน ให้ดูเหมือน "โฟลเดอร์ซ้อนไอคอน" มาตรฐาน
         */
        private fun composeStackBitmap(icons: List<Drawable>): Bitmap {
            val canvasSize = 192 // px ของบิตแมปผลลัพธ์ (สเกลลงเองตอนแสดงใน ImageView)
            val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val cell = canvasSize / 2
            val gap = 6
            val positions = listOf(
                Pair(0, 0), Pair(cell, 0), Pair(0, cell), Pair(cell, cell)
            )

            for (i in icons.indices) {
                if (i >= positions.size) break
                val (x, y) = positions[i]
                val d = icons[i]
                d.setBounds(x + gap, y + gap, x + cell - gap, y + cell - gap)
                d.draw(canvas)
            }
            return bitmap
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
