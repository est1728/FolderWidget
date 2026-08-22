package com.est.folderwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * วิตเจ็ตโฟลเดอร์ — ตัวเดียวสำหรับทุกสไตล์
 *
 * เดิมมีการคำนวณ "ระดับเลย์เอาต์" จากขนาด dp จริงที่ launcher รายงานมาให้ (dpTier)
 * ผสมกับขนาดที่ผู้ใช้เลือกไว้ในหน้าตั้งค่า (presetTier) ซึ่งเป็นต้นเหตุบั๊กที่แก้ไม่หาย
 * สักที: พอผู้ใช้แก้ไขแอพในโฟลเดอร์ (เพิ่ม/ลบ/สลับตำแหน่ง) แล้ว widget ถูกวาดใหม่
 * บางเครื่อง/บาง launcher จะรายงานขนาด dp ที่ต่างจากตอนวางครั้งแรกเล็กน้อย ทำให้
 * เลย์เอาต์ถูกเปลี่ยนไปแบบไม่ตั้งใจ (กลายเป็น "กริดธรรมดา" ที่ผู้ใช้ไม่ได้เลือก)
 *
 * ตอนนี้ตัดการคำนวณจากขนาดจริงออกทั้งหมด — ใช้ "สไตล์ที่ผู้ใช้เลือกไว้ในแอพ" เพียง
 * อย่างเดียวเป็นตัวตัดสินหน้าตาของ widget เสมอ ไม่ขึ้นกับ dp ที่วัดได้จากเครื่องอีกต่อไป
 * รับประกันว่าหน้าตาจะไม่เปลี่ยนเองไม่ว่าจะแก้ไขโฟลเดอร์กี่ครั้งก็ตาม
 */
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            PrefsHelper.deleteWidgetData(context, id)
        }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_folder)
            val packages = PrefsHelper.getPackages(context, widgetId)
            val style = PrefsHelper.getSize(context, widgetId)

            val bitmap = FolderIconComposer.compose(context, packages, style)
            views.setImageViewBitmap(R.id.widget_icon, bitmap)

            val openPopupIntent = Intent(context, FolderPopupActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val animOptions = android.app.ActivityOptions.makeCustomAnimation(
                context, R.anim.popup_enter, R.anim.popup_exit
            )
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openPopupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                animOptions.toBundle()
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
