package com.est.folderwidget

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter สำหรับกริดไอคอนในหน้า popup
 * รองรับ:
 *  - ลากสลับตำแหน่ง (reorder) ผ่าน ItemTouchHelper ที่ผูกไว้ใน FolderPopupActivity
 *  - ปัดขึ้น (swipe up) เพื่อลบออกจากโฟลเดอร์ - ท่าทางมาตรฐานที่ ItemTouchHelper รองรับ
 *    (การ "ลากออกนอกกรอบการ์ด" ต้องใช้ touch listener แยกเพิ่มเติม ถ้าต้องการ
 *    ความรู้สึกแบบ iOS/MIUI เป๊ะ แจ้งได้ จะเพิ่ม custom drag shadow ให้ทีหลัง)
 */
class DraggableAppAdapter(
    private val items: MutableList<AppItem>,
    private val onAppClick: (AppItem) -> Unit,
    private val onOrderChanged: (List<AppItem>) -> Unit,
    private val iconLoader: (String) -> android.graphics.drawable.Drawable?
) : RecyclerView.Adapter<DraggableAppAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.icon.setImageDrawable(iconLoader(item.packageName))
        holder.itemView.setOnClickListener { onAppClick(item) }
    }

    override fun getItemCount() = items.size

    /** เรียกจาก ItemTouchHelper.Callback ตอนลากสลับตำแหน่ง */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    /** เรียกจาก ItemTouchHelper.Callback ตอนปัดออก (ลบ) */
    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
        onOrderChanged(items.toList())
    }

    /** เรียกตอนปล่อยนิ้วหลังลากเสร็จ เพื่อบันทึกลำดับใหม่ */
    fun commitOrder() {
        onOrderChanged(items.toList())
    }

    fun currentItems(): List<AppItem> = items.toList()
}
