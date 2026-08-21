package com.est.folderwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FolderStackConfigActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val selectedPackages = mutableSetOf<String>()
    private lateinit var nameEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_config_stack)

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

        val apps = AppRepository.getLaunchableApps(this)
        val recycler = findViewById<RecyclerView>(R.id.app_list_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InstalledAppsAdapter(
            allApps = apps,
            selected = selectedPackages,
            iconLoader = { pkg -> AppRepository.loadIcon(this, pkg) },
            onSelectionChanged = { /* เก็บลง selectedPackages realtime อยู่แล้ว ไม่จำกัดจำนวน */ }
        )

        findViewById<android.widget.ImageButton>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btn_save).setOnClickListener { saveAndFinish() }
    }

    private fun saveAndFinish() {
        val name = nameEdit.text.toString().ifBlank { "โฟลเดอร์" }
        PrefsHelper.saveFolderName(this, widgetId, name)
        PrefsHelper.savePackages(this, widgetId, selectedPackages.toList())

        val appWidgetManager = AppWidgetManager.getInstance(this)
        FolderStackWidgetProvider.updateWidget(this, appWidgetManager, widgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
