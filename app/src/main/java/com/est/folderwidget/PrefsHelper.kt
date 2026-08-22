package com.est.folderwidget

import android.content.Context

/**
 * เก็บข้อมูลของแต่ละโฟลเดอร์ (widgetId) ลง SharedPreferences:
 * - ชื่อโฟลเดอร์
 * - สไตล์ที่เลือก (normal / expand / xxl) — ตัวนี้เป็นตัวกำหนดหน้าตา widget
 *   เพียงอย่างเดียวเสมอ (ดูเหตุผลใน FolderWidgetProvider — ไม่มีการคำนวณจาก
 *   ขนาด dp จริงของ widget อีกต่อไป กัน widget เปลี่ยนหน้าตาเองแบบไม่ตั้งใจ)
 * - ลำดับแอพ (เก็บเป็น package name คั่นด้วย , ตามลำดับที่ผู้ใช้ลากจัดเรียง)
 *
 * ใช้ SharedPreferences ธรรมดาพอสำหรับข้อมูลขนาดเล็กแบบนี้ ไม่ต้องพึ่ง DB
 */
object PrefsHelper {

    private const val PREFS_NAME = "folder_widget_prefs"
    const val SIZE_NORMAL = "normal"
    const val SIZE_EXPAND = "expand"
    const val SIZE_XXL = "xxl"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveFolderName(context: Context, widgetId: Int, name: String) {
        prefs(context).edit().putString("name_$widgetId", name).apply()
    }

    fun getFolderName(context: Context, widgetId: Int): String =
        prefs(context).getString("name_$widgetId", "โฟลเดอร์") ?: "โฟลเดอร์"

    fun saveSize(context: Context, widgetId: Int, size: String) {
        prefs(context).edit().putString("size_$widgetId", size).apply()
    }

    fun getSize(context: Context, widgetId: Int): String =
        prefs(context).getString("size_$widgetId", SIZE_NORMAL) ?: SIZE_NORMAL

    /** บันทึกลำดับแอพ (เรียกซ้ำได้ทุกครั้งที่ลากจัดเรียงเสร็จ) */
    fun savePackages(context: Context, widgetId: Int, packages: List<String>) {
        prefs(context).edit()
            .putString("packages_$widgetId", packages.joinToString(","))
            .apply()
    }

    fun getPackages(context: Context, widgetId: Int): List<String> {
        val raw = prefs(context).getString("packages_$widgetId", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    fun deleteWidgetData(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("name_$widgetId")
            .remove("size_$widgetId")
            .remove("packages_$widgetId")
            .apply()
    }
}
