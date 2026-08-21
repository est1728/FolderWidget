package com.est.folderwidget

/**
 * แทนแอพหนึ่งตัวที่อยู่ในโฟลเดอร์ (หรือในลิสต์แอพทั้งหมดของเครื่อง)
 */
data class AppItem(
    val packageName: String,
    val label: String
)
