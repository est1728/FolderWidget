package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class FolderPopupActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var adapter: DraggableAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        val titleView = findViewById<android.widget.TextView>(R.id.popup_title)
        titleView.text = PrefsHelper.getFolderName(this, widgetId)

        val packages = PrefsHelper.getPackages(this, widgetId).toMutableList()
        val pm = packageManager
        val items = packages.map { pkg ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            AppItem(pkg, label)
        }.toMutableList()

        adapter = DraggableAppAdapter(
            items = items,
            onAppClick = { app ->
                AppRepository.launch(this, app.packageName)
                finishSmoothly()
            },
            onOrderChanged = { newOrder ->
                PrefsHelper.savePackages(this, widgetId, newOrder.map { it.packageName })
                refreshWidget()
            },
            iconLoader = { pkg -> AppRepository.loadIcon(this, pkg) }
        )

        val recycler = findViewById<RecyclerView>(R.id.popup_recycler)
        val columns = when (PrefsHelper.getSize(this, widgetId)) {
            PrefsHelper.SIZE_XXL -> 5
            PrefsHelper.SIZE_EXPAND -> 4
            else -> 3
        }
        recycler.layoutManager = GridLayoutManager(this, columns)
        recycler.adapter = adapter

        // ผูก ItemTouchHelper: ลากสลับตำแหน่งได้ทุกทิศทาง + ปัดขึ้นเพื่อลบ
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ItemTouchHelper.UP
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // removeItem บันทึกลำดับใหม่และรีเฟรช widget ให้เองผ่าน onOrderChanged
                adapter.removeItem(viewHolder.bindingAdapterPosition)
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                // ปล่อยนิ้วหลังลากสลับตำแหน่งเสร็จ -> บันทึกลำดับ
                adapter.commitOrder()
            }
        })
        touchHelper.attachToRecyclerView(recycler)

        // แตะพื้นหลังโปร่งใส (นอกการ์ด) เพื่อปิด popup เหมือนโฟลเดอร์จริง
        findViewById<android.view.View>(R.id.popup_root).setOnClickListener {
            finishSmoothly()
        }
        findViewById<android.view.View>(R.id.popup_card).setOnClickListener {
            // กันไม่ให้แตะบนการ์ดแล้วทะลุไปโดน scrim ด้านหลัง
        }
    }

    private fun refreshWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        FolderWidgetProvider.updateWidget(this, appWidgetManager, widgetId)
    }

    private fun finishSmoothly() {
        finish()
        overridePendingTransition(0, R.anim.popup_exit)
    }

    override fun onBackPressed() {
        finishSmoothly()
    }
}
