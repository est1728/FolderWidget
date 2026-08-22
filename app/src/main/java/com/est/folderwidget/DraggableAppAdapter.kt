package com.est.folderwidget

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter สำหรับกริดไอคอนในหน้า popup
 *
 * โหมดปกติ: แตะ = เปิดแอพ, กดค้าง = เริ่มลากสลับตำแหน่ง (ผ่าน ItemTouchHelper
 * แบบมาตรฐานของ Android — ของเดิมที่เคยลองทำ touch-listener เองแยกจังหวะ
 * "กดค้างนิ่งๆ" กับ "กดแล้วลาก" กลับไปชนกับการจัดการทัชของ RecyclerView เอง
 * จนลากไม่ได้เลยในบางเครื่อง เลยกลับมาใช้กลไกมาตรฐานที่เสถียรกว่าแทน)
 *
 * โหมดแก้ไข (editMode): เข้าโหมดนี้เมื่อเริ่มลากไอคอนตัวแรก (ดู FolderPopupActivity
 * ที่ hook เข้า ItemTouchHelper.Callback.onSelectedChanged) ทุกไอคอนจะโชว์วงกลม
 * เล็กๆ ให้ติ๊กเลือกได้ แตะไอคอนตอนนี้จะ toggle การเลือกแทนการเปิดแอพ
 */
class DraggableAppAdapter(
    private val items: MutableList<AppItem>,
    private val onAppClick: (AppItem) -> Unit,
    private val onOrderChanged: (List<AppItem>) -> Unit,
    private val selectedPackages: MutableSet<String>,
    private val onToggleSelect: (AppItem) -> Unit
) : RecyclerView.Adapter<DraggableAppAdapter.VH>() {

    /** true ระหว่างอยู่ในโหมดแก้ไข (multi-select) */
    var editMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var iconLoader: (String) -> android.graphics.drawable.Drawable? = { null }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
        val selectCircle: ImageView = view.findViewById(R.id.select_circle)
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

        holder.selectCircle.visibility = if (editMode) android.view.View.VISIBLE else android.view.View.GONE
        holder.selectCircle.setImageResource(
            if (selectedPackages.contains(item.packageName)) R.drawable.ic_select_on else R.drawable.ic_select_off
        )

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (editMode) {
                onToggleSelect(items[pos])
                notifyItemChanged(pos)
            } else {
                onAppClick(items[pos])
            }
        }
    }

    override fun getItemCount() = items.size

    /** เรียกจาก ItemTouchHelper.Callback ตอนลากสลับตำแหน่ง */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    /** เรียกตอนลากออกนอกการ์ด หรือวางบนปุ่มลบ -> ลบออกจากลิสต์ */
    fun removeItems(packages: Set<String>) {
        if (packages.isEmpty()) return
        items.removeAll { it.packageName in packages }
        notifyDataSetChanged()
        onOrderChanged(items.toList())
    }

    fun removeItem(item: AppItem) = removeItems(setOf(item.packageName))

    /** เพิ่มแอพใหม่จากหน้าต่าง "+" แล้วบันทึก+รีเฟรช widget ทันที */
    fun addItems(newItems: List<AppItem>) {
        val startPos = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPos, newItems.size)
        onOrderChanged(items.toList())
    }

    /** เรียกจาก ItemTouchHelper.Callback ตอนปล่อยนิ้วหลังลากสลับตำแหน่งเสร็จ */
    fun commitOrder() {
        onOrderChanged(items.toList())
    }

    fun currentItems(): List<AppItem> = items.toList()

    fun indexOf(packageName: String): Int = items.indexOfFirst { it.packageName == packageName }
}
