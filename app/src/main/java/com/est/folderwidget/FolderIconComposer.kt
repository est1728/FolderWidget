package com.est.folderwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * ประกอบไอคอนแอพในโฟลเดอร์ให้เป็น "บิตแมปเดียว" ตามสไตล์ที่เลือกไว้
 * (ปกติ / ขยาย / XXL) — ใช้ฟังก์ชันเดียวกันทั้งตอนวาด widget จริงบนหน้าโฮม
 * และตอนวาดพรีวิวในหน้าตั้งค่า เพื่อให้พรีวิวตรงกับของจริงเป๊ะๆ เสมอ
 * ไม่มีทางเพี้ยน/ไม่ตรงกันอีกต่อไป เพราะเรียกจากจุดเดียวกัน
 *
 * ทั้ง 3 สไตล์ออกแบบให้อยู่ในพื้นที่ไอคอนแอพ "1 ช่องกริดเดียว" (เหมือนไอคอนแอพปกติ)
 * ไม่ใช่ widget ที่กินหลายช่องแบบเดิม:
 *  - ปกติ: การ์ดโค้งมน ใส่ไอคอนแอพย่อเล็กเรียงกริด 3x3 (สูงสุด 9 แอพ) ข้างในช่องเดียว
 *          เหมือนไอคอนโฟลเดอร์จริงของระบบ (MIUI/Android เนทีฟ)
 *  - ขยาย: โชว์ไอคอนเต็มขนาด 3 ตัวแรก + ช่องที่ 4 เป็นคอลลาจของแอพที่เหลือ (สไตล์ "ซ้อนโฟลเดอร์")
 *  - XXL: เหมือนขยาย แต่โมเสกแอพที่เหลือในช่องที่ 4 หนาแน่นกว่า (มองเห็นแอพได้เยอะกว่า)
 */
object FolderIconComposer {

    fun compose(context: Context, packages: List<String>, style: String, canvasPx: Int = 240): Bitmap {
        return when (style) {
            PrefsHelper.SIZE_EXPAND -> composeStack(context, packages, canvasPx, collageMax = 4)
            PrefsHelper.SIZE_XXL -> composeStack(context, packages, canvasPx, collageMax = 9)
            else -> composeGrid(context, packages, canvasPx, cols = 3, maxIcons = 9)
        }
    }

    /** สไตล์ "ปกติ" — การ์ดโค้งมนใส่ไอคอนย่อเรียงกริดเล็กๆ ข้างใน (เหมือนโฟลเดอร์จริงของระบบ) */
    private fun composeGrid(context: Context, packages: List<String>, canvasPx: Int, cols: Int, maxIcons: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasPx, canvasPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawFolderCardBackground(canvas, canvasPx)

        val shown = packages.take(maxIcons)
        val rows = if (shown.isEmpty()) 1 else (shown.size + cols - 1) / cols.coerceAtLeast(1)
        val gridPad = canvasPx * 0.12f
        val gridSize = canvasPx - gridPad * 2
        val cell = gridSize / cols
        val iconGap = cell * 0.14f

        // จัดกึ่งกลางแนวตั้งถ้าแอพไม่เต็มแถวสุดท้าย
        val usedRows = if (shown.isEmpty()) 1 else ((shown.size - 1) / cols) + 1
        val verticalOffset = (canvasPx - usedRows * cell) / 2f

        for ((i, pkg) in shown.withIndex()) {
            val col = i % cols
            val row = i / cols
            val left = gridPad + col * cell + iconGap
            val top = verticalOffset + row * cell + iconGap
            val right = gridPad + (col + 1) * cell - iconGap
            val bottom = verticalOffset + (row + 1) * cell - iconGap
            val icon = AppRepository.loadIcon(context, pkg) ?: continue
            icon.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            icon.draw(canvas)
        }
        return bitmap
    }

    /** สไตล์ "ขยาย"/"XXL" — 3 ไอคอนเต็มขนาดแรก + ช่องที่ 4 เป็นคอลลาจแอพที่เหลือ */
    private fun composeStack(context: Context, packages: List<String>, canvasPx: Int, collageMax: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasPx, canvasPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawFolderCardBackground(canvas, canvasPx)

        val pad = canvasPx * 0.08f
        val gap = canvasPx * 0.05f
        val cell = (canvasPx - pad * 2 - gap) / 2f
        val positions = listOf(
            Pair(pad, pad),
            Pair(pad + cell + gap, pad),
            Pair(pad, pad + cell + gap)
        )

        for (i in 0 until 3) {
            if (i >= packages.size) break
            val icon = AppRepository.loadIcon(context, packages[i]) ?: continue
            val (x, y) = positions[i]
            val iconPad = cell * 0.08f
            icon.setBounds(
                (x + iconPad).toInt(), (y + iconPad).toInt(),
                (x + cell - iconPad).toInt(), (y + cell - iconPad).toInt()
            )
            icon.draw(canvas)
        }

        // ช่องที่ 4 (ล่างขวา)
        val fx = pad + cell + gap
        val fy = pad + cell + gap
        when {
            packages.size > 4 -> {
                val remaining = packages.subList(3, packages.size).take(collageMax)
                drawMiniCollage(context, canvas, remaining, fx, fy, cell)
            }
            packages.size == 4 -> {
                val icon = AppRepository.loadIcon(context, packages[3])
                if (icon != null) {
                    val iconPad = cell * 0.08f
                    icon.setBounds(
                        (fx + iconPad).toInt(), (fy + iconPad).toInt(),
                        (fx + cell - iconPad).toInt(), (fy + cell - iconPad).toInt()
                    )
                    icon.draw(canvas)
                }
            }
            // ถ้ามี <=3 แอพ ก็เว้นช่องที่ 4 ว่างไว้ (ไม่วาดอะไร)
        }
        return bitmap
    }

    /** วาดโมเสกแอพที่เหลือย่อเล็กๆ ลงในช่องเดียว (ใช้เป็นช่องที่ 4 ของสไตล์ขยาย/XXL) */
    private fun drawMiniCollage(context: Context, canvas: Canvas, packages: List<String>, x: Float, y: Float, size: Float) {
        if (packages.isEmpty()) return
        val cols = if (packages.size <= 4) 2 else 3
        val rows = ((packages.size - 1) / cols) + 1
        val cellW = size / cols
        val cellH = size / rows
        val gap = size * 0.06f

        for ((i, pkg) in packages.withIndex()) {
            val icon = AppRepository.loadIcon(context, pkg) ?: continue
            val col = i % cols
            val row = i / cols
            val left = x + col * cellW + gap
            val top = y + row * cellH + gap
            val right = x + (col + 1) * cellW - gap
            val bottom = y + (row + 1) * cellH - gap
            icon.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            icon.draw(canvas)
        }
    }

    private fun drawFolderCardBackground(canvas: Canvas, canvasPx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6EEEEEE")
        }
        val radius = canvasPx * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, canvasPx.toFloat(), canvasPx.toFloat()), radius, radius, paint)
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
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
