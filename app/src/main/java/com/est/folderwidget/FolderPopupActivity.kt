package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FolderPopupActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var adapter: DraggableAppAdapter
    private lateinit var popupCard: View
    private lateinit var titleView: android.widget.TextView
    private lateinit var doneButton: android.widget.TextView
    private lateinit var dropzoneBar: View
    private lateinit var dropzoneRemove: View
    private lateinit var dropzoneUninstall: View
    private lateinit var dragOverlay: FrameLayout

    /** แอพที่ถูกติ๊กเลือกไว้ในโหมดแก้ไข (multi-select) */
    private val selectedPackages = mutableSetOf<String>()

    // true ระหว่างที่นิ้วลากไอคอนออกไปนอกขอบการ์ดแล้ว (เตรียมลบทันทีที่ปล่อยนิ้ว)
    private var draggingOutsideCard = false

    // ไอคอน "เงา" ของแอพอื่นๆ ที่เลือกไว้ (ไม่ใช่ตัวที่กำลังลากจริง) ที่ลอยตามนิ้วไปด้วย
    private var ghostViews: List<ImageView> = emptyList()
    private var armedDropzone: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // วาดเนื้อหาเต็มจอ ทับ status bar / navigation bar เอง
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_popup)
        // ต้องเรียกหลัง setContentView เท่านั้น (ก่อนหน้านี้เรียกก่อนแล้ว crash เพราะ
        // DecorView ยังไม่ถูกสร้าง)
        setupBlurAndEdges()

        val root = findViewById<View>(R.id.popup_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        titleView = findViewById(R.id.popup_title)
        titleView.text = PrefsHelper.getFolderName(this, widgetId)
        titleView.setOnClickListener { showRenameDialog() }
        popupCard = findViewById(R.id.popup_card)
        doneButton = findViewById(R.id.popup_btn_done)
        dropzoneBar = findViewById(R.id.dropzone_bar)
        dropzoneRemove = findViewById(R.id.dropzone_remove)
        dropzoneUninstall = findViewById(R.id.dropzone_uninstall)
        dragOverlay = findViewById(R.id.drag_overlay)
        doneButton.setOnClickListener { exitEditMode() }

        val packages = PrefsHelper.getPackages(this, widgetId)
        val items = packages.map { pkg -> toAppItem(pkg) }.toMutableList()

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
            selectedPackages = selectedPackages,
            onToggleSelect = { app ->
                if (!selectedPackages.remove(app.packageName)) {
                    selectedPackages.add(app.packageName)
                }
            }
        )
        adapter.iconLoader = { pkg -> AppRepository.loadIcon(this, pkg) }

        val recycler = findViewById<RecyclerView>(R.id.popup_recycler)
        val columns = when (PrefsHelper.getSize(this, widgetId)) {
            PrefsHelper.SIZE_XXL -> 5
            PrefsHelper.SIZE_EXPAND -> 4
            else -> 3
        }
        recycler.layoutManager = GridLayoutManager(this, columns)
        recycler.adapter = adapter

        // ItemTouchHelper: ใช้กลไก long-press-drag มาตรฐานของ Android (เสถียรกว่า
        // การจับจังหวะทัชเองที่เคยลองทำแล้วชนกับ RecyclerView จนลากไม่ได้)
        // เมื่อเริ่มลาก -> เข้าโหมดแก้ไขทันที (onSelectedChanged) เหมือนระบบจริงที่
        // กดค้างแล้วเข้าโหมด "จัดเรียง/เลือกหลายแอพ"
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            private var draggedPackage: String? = null

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    val pos = viewHolder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val app = adapter.currentItems()[pos]
                    draggedPackage = app.packageName
                    if (!adapter.editMode) {
                        enterEditMode()
                    }
                    // ตัวที่กำลังลากอยู่ ถือเป็นส่วนหนึ่งของ "กลุ่มที่กำลังย้าย" เสมอ
                    // (ไม่ต้องเลือกไว้ล่วงหน้าก็ลากมันเองได้ตามปกติ)
                    dropzoneBar.visibility = View.VISIBLE
                    setupGhosts(draggedPackage!!)
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    clearGhosts()
                    dropzoneBar.visibility = View.GONE
                    armedDropzone?.setBackgroundResource(R.drawable.bg_dropzone)
                    armedDropzone = null
                    draggedPackage = null
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return

                val loc = IntArray(2)
                viewHolder.itemView.getLocationOnScreen(loc)
                val itemCenterX = loc[0] + viewHolder.itemView.width / 2
                val itemCenterY = loc[1] + viewHolder.itemView.height / 2

                // อัปเดตตำแหน่งไอคอนเงาให้ลอยตามนิ้ว (ตามตัวที่กำลังลากจริง)
                positionGhosts(itemCenterX, itemCenterY)

                // เช็คว่ากำลังลากอยู่เหนือ drop zone ไหนอยู่หรือเปล่า (ไฮไลต์ให้เห็น)
                val hit = hitTestDropzone(itemCenterX, itemCenterY)
                if (hit != armedDropzone) {
                    armedDropzone?.setBackgroundResource(R.drawable.bg_dropzone)
                    hit?.setBackgroundResource(R.drawable.bg_dropzone_armed)
                    armedDropzone = hit
                }

                // ลากออกนอกการ์ด (ด้านบน) ก็ยังลบได้เหมือนเดิม เผื่อ dropzone ยังไม่โผล่ทัน
                val cardLoc = IntArray(2)
                popupCard.getLocationOnScreen(cardLoc)
                val outside = itemCenterY < cardLoc[1] - 24
                if (outside != draggingOutsideCard) {
                    draggingOutsideCard = outside
                    viewHolder.itemView.alpha = if (outside) 0.35f else 1f
                }
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.alpha = 1f
                val pos = viewHolder.bindingAdapterPosition
                val draggedPkg = draggedPackage
                val bundle = (selectedPackages + setOfNotNull(draggedPkg)).toSet()

                when {
                    armedDropzone === dropzoneRemove -> {
                        adapter.removeItems(bundle)
                        selectedPackages.clear()
                        exitEditMode()
                    }
                    armedDropzone === dropzoneUninstall -> {
                        uninstallApps(bundle)
                        selectedPackages.clear()
                        exitEditMode()
                    }
                    draggingOutsideCard -> {
                        draggingOutsideCard = false
                        if (pos != RecyclerView.NO_POSITION) {
                            adapter.removeItems(setOfNotNull(draggedPkg))
                        }
                    }
                    else -> {
                        // ปล่อยนิ้วในการ์ดปกติ (ไม่ได้วางบน dropzone) -> แค่บันทึกลำดับใหม่
                        adapter.commitOrder()
                    }
                }
                armedDropzone?.setBackgroundResource(R.drawable.bg_dropzone)
                armedDropzone = null
                clearGhosts()
                dropzoneBar.visibility = View.GONE
            }
        })
        touchHelper.attachToRecyclerView(recycler)

        findViewById<android.widget.ImageButton>(R.id.popup_btn_add).setOnClickListener {
            showAddAppDialog()
        }

        // แตะพื้นหลังโปร่งใส (นอกการ์ด) เพื่อปิด popup เหมือนโฟลเดอร์จริง
        root.setOnClickListener { finishSmoothly() }
        findViewById<View>(R.id.popup_card).setOnClickListener {
            // กันไม่ให้แตะบนการ์ดแล้วทะลุไปโดน scrim ด้านหลัง
        }
    }

    /** เข้าโหมดแก้ไข: ทุกไอคอนโชว์วงกลมติ๊กเลือก + ปุ่ม "เสร็จ" โผล่แทนชื่อโฟลเดอร์ */
    private fun enterEditMode() {
        adapter.editMode = true
        titleView.visibility = View.GONE
        doneButton.visibility = View.VISIBLE
    }

    private fun exitEditMode() {
        adapter.editMode = false
        selectedPackages.clear()
        titleView.visibility = View.VISIBLE
        doneButton.visibility = View.GONE
        dropzoneBar.visibility = View.GONE
    }

    /**
     * สร้างไอคอน "เงา" ของแอพอื่นๆ ที่เลือกไว้ (ไม่รวมตัวที่กำลังลากจริง ซึ่ง
     * ItemTouchHelper วาดให้เองอยู่แล้ว) ใส่ใน overlay ลอยด้านบนสุด เพื่อให้
     * ดูเหมือน "ไอคอนทั้งหมดถูกรวบมาที่นิ้ว" ตอนลาก
     */
    private fun setupGhosts(draggedPackage: String) {
        clearGhosts()
        val others = selectedPackages.filter { it != draggedPackage }.take(3)
        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        ghostViews = others.mapIndexed { index, pkg ->
            ImageView(this).apply {
                setImageDrawable(AppRepository.loadIcon(this@FolderPopupActivity, pkg))
                alpha = 0.85f
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(size, size)
                elevation = (index + 1).toFloat()
                dragOverlay.addView(this)
            }
        }
    }

    private fun positionGhosts(centerX: Int, centerY: Int) {
        val density = resources.displayMetrics.density
        val offset = (10 * density).toInt()
        for ((i, ghost) in ghostViews.withIndex()) {
            val dx = (i + 1) * offset
            val dy = (i + 1) * offset
            ghost.x = (centerX - ghost.width / 2 + dx).toFloat()
            ghost.y = (centerY - ghost.height / 2 + dy).toFloat()
        }
    }

    private fun clearGhosts() {
        ghostViews.forEach { dragOverlay.removeView(it) }
        ghostViews = emptyList()
    }

    private fun hitTestDropzone(x: Int, y: Int): View? {
        if (dropzoneBar.visibility != View.VISIBLE) return null
        for (zone in listOf(dropzoneRemove, dropzoneUninstall)) {
            val loc = IntArray(2)
            zone.getLocationOnScreen(loc)
            if (x in loc[0]..(loc[0] + zone.width) && y in loc[1]..(loc[1] + zone.height)) {
                return zone
            }
        }
        return null
    }

    private fun uninstallApps(packages: Set<String>) {
        if (packages.isEmpty()) return
        adapter.removeItems(packages)
        // ระบบให้ถอนได้ทีละแอพต่อ 1 intent เท่านั้น เปิดหน้าถอนติดตั้งไล่ทีละตัว
        for (pkg in packages) {
            val uri = Uri.parse("package:$pkg")
            startActivity(Intent(Intent.ACTION_DELETE, uri))
        }
    }

    /**
     * เปิด backdrop blur จริงของระบบ (มองทะลุเห็นวอลเปเปอร์/โฮมสกรีนเบลอๆ ด้านหลัง
     * เหมือนป๊อปอัพของ MIUI/HyperOS) บนเครื่องที่รองรับ (Android 12 / API 31 ขึ้นไป)
     */
    private fun setupBlurAndEdges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(90)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val card = findViewById<androidx.cardview.widget.CardView>(R.id.popup_card)
            card.setCardBackgroundColor(getColor(R.color.popup_card_bg_no_blur))
        }
    }

    private fun showRenameDialog() {
        val input = android.widget.EditText(this).apply {
            setText(PrefsHelper.getFolderName(this@FolderPopupActivity, widgetId))
            setSelection(text.length)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.config_hint)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().ifBlank { getString(R.string.default_folder_name) }
                PrefsHelper.saveFolderName(this, widgetId, name)
                titleView.text = name
                refreshWidget()
            }
            .show()
    }

    private fun toAppItem(pkg: String): AppItem {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        return AppItem(pkg, label)
    }

    private fun showAddAppDialog() {
        val currentPackages = adapter.currentItems().map { it.packageName }.toSet()
        val candidates = AppRepository.getLaunchableApps(this)
            .filter { it.packageName !in currentPackages }
        val selected = mutableSetOf<String>()

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@FolderPopupActivity)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        recycler.adapter = InstalledAppsAdapter(
            allApps = candidates,
            selected = selected,
            iconLoader = { pkg -> AppRepository.loadIcon(this, pkg) },
            onSelectionChanged = { /* เก็บลง selected แบบ realtime อยู่แล้ว */ }
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.add_app_dialog_title)
            .setView(recycler)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val toAdd = candidates.filter { it.packageName in selected }
                if (toAdd.isNotEmpty()) {
                    adapter.addItems(toAdd)
                }
            }
            .show()
    }

    private fun refreshWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        FolderWidgetProvider.updateWidget(this, appWidgetManager, widgetId)
    }

    private fun finishSmoothly() {
        finish()
        overridePendingTransition(0, R.anim.popup_exit)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter.editMode) {
            exitEditMode()
        } else {
            finishSmoothly()
        }
    }
}
