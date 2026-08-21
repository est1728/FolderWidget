package com.est.folderwidget

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InstalledAppsAdapter(
    private val allApps: List<AppItem>,
    private val selected: MutableSet<String>,
    private val iconLoader: (String) -> android.graphics.drawable.Drawable?,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<InstalledAppsAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_checkbox, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = allApps[position]
        holder.name.text = app.label
        holder.icon.setImageDrawable(iconLoader(app.packageName))
        holder.checkbox.isChecked = selected.contains(app.packageName)

        holder.itemView.setOnClickListener {
            if (selected.contains(app.packageName)) {
                selected.remove(app.packageName)
            } else {
                selected.add(app.packageName)
            }
            holder.checkbox.isChecked = selected.contains(app.packageName)
            onSelectionChanged()
        }
    }

    override fun getItemCount() = allApps.size
}
