package com.est.folderwidget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * RecyclerView ธรรมดา แต่จำกัด "ความสูงสูงสุด" ตอน layout_height="wrap_content" ได้
 * (RecyclerView ปกติไม่รองรับ android:maxHeight)
 *
 * แก้ปัญหาการ์ดโฟลเดอร์ในหน้า popup ที่เคยสูงตายตัว 360dp เสมอ ไม่ว่าจะมี
 * แอพกี่ตัว — ตอนนี้ปล่อยให้สูงตามจำนวนแอพจริง (wrap) แต่ถ้าแอพเยอะมาก
 * ก็จะหยุดที่ maxHeightDp แล้วให้ RecyclerView เลื่อน (scroll) แทนที่จะดันการ์ด
 * ยืดจนล้นจอ
 */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var maxHeightPx: Int = Int.MAX_VALUE

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.MaxHeightRecyclerView)
            val maxHeightDp = a.getInteger(R.styleable.MaxHeightRecyclerView_maxHeightDp, -1)
            if (maxHeightDp > 0) {
                maxHeightPx = (maxHeightDp * resources.displayMetrics.density).roundToInt()
            }
            a.recycle()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightSpec)
        val boundedHeightSpec = if (heightMode != MeasureSpec.EXACTLY) {
            val heightSize = MeasureSpec.getSize(heightSpec)
            val newSize = if (heightSize == 0) maxHeightPx else min(heightSize, maxHeightPx)
            MeasureSpec.makeMeasureSpec(newSize, MeasureSpec.AT_MOST)
        } else {
            heightSpec
        }
        super.onMeasure(widthSpec, boundedHeightSpec)
    }
}
