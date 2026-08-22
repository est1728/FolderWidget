package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FolderConfigActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedSize: String = PrefsHelper.SIZE_NORMAL
    private val selectedPackages = mutableSetOf<String>()
    private lateinit var nameEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ถ้าผู้ใช้กดปิดกลางทาง ให้ widget ไม่ถูกสร้าง (พฤติกรรมมาตรฐานของ widget config)
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        nameEdit = findViewById(R.id.edit_folder_name)
        nameEdit.setText(PrefsHelper.getFolderName(this, widgetId))

        selectedPackages.addAll(PrefsHelper.getPackages(this, widgetId))
        selectedSize = PrefsHelper.getSize(this, widgetId)

        setupSizePicker()
        setupAppList()
        refreshPreview()

        findViewById<android.widget.ImageButton>(R.id.btn_close).setOnClickListener {
            finish()
        }
        findViewById<android.widget.ImageButton>(R.id.btn_save).setOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupSizePicker() {
        val normal = findViewById<LinearLayout>(R.id.size_normal_container)
        val expand = findViewById<LinearLayout>(R.id.size_expand_container)
        val xxl = findViewById<LinearLayout>(R.id.size_xxl_container)
        val containers = mapOf(
            PrefsHelper.SIZE_NORMAL to normal,
            PrefsHelper.SIZE_EXPAND to expand,
            PrefsHelper.SIZE_XXL to xxl
        )

        fun refreshSelection() {
            containers.forEach { (key, view) ->
                view.getChildAt(0).isSelected = (key == selectedSize)
            }
        }
        refreshSelection()

        containers.forEach { (key, view) ->
            view.setOnClickListener {
                selectedSize = key
                refreshSelection()
                refreshPreview()
            }
        }
    }

    private fun setupAppList() {
        val apps = AppRepository.getLaunchableApps(this)
        val recycler = findViewById<RecyclerView>(R.id.app_list_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InstalledAppsAdapter(
            allApps = apps,
            selected = selectedPackages,
            iconLoader = { pkg -> AppRepository.loadIcon(this, pkg) },
            onSelectionChanged = { refreshPreview() }
        )
    }

    /**
     * วาดพรีวิวด้วยฟังก์ชันตัวเดียวกับที่ widget จริงใช้ (FolderIconComposer)
     * รับประกันว่าพรีวิวตรงกับของจริงเป๊ะๆ เสมอ ไม่มีทางเพี้ยน/ว่างเปล่าอีกต่อไป
     * และยังโชว์มินิพรีวิวบนปุ่มเลือกขนาดทั้ง 3 แบบด้วย (เหมือนของระบบ MIUI จริง)
     */
    private fun refreshPreview() {
        val packages = selectedPackages.toList()

        findViewById<ImageView>(R.id.preview_image).setImageBitmap(
            FolderIconComposer.compose(this, packages, selectedSize, canvasPx = 320)
        )
        findViewById<ImageView>(R.id.size_preview_normal).setImageBitmap(
            FolderIconComposer.compose(this, packages, PrefsHelper.SIZE_NORMAL, canvasPx = 160)
        )
        findViewById<ImageView>(R.id.size_preview_expand).setImageBitmap(
            FolderIconComposer.compose(this, packages, PrefsHelper.SIZE_EXPAND, canvasPx = 160)
        )
        findViewById<ImageView>(R.id.size_preview_xxl).setImageBitmap(
            FolderIconComposer.compose(this, packages, PrefsHelper.SIZE_XXL, canvasPx = 160)
        )
    }

    private fun saveAndFinish() {
        val name = nameEdit.text.toString().ifBlank { "โฟลเดอร์" }
        PrefsHelper.saveFolderName(this, widgetId, name)
        PrefsHelper.saveSize(this, widgetId, selectedSize)
        PrefsHelper.savePackages(this, widgetId, selectedPackages.toList())

        val appWidgetManager = AppWidgetManager.getInstance(this)
        FolderWidgetProvider.updateWidget(this, appWidgetManager, widgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
