package com.est.folderwidget

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.TextView>(R.id.text_version).text =
            "เวอร์ชัน ${BuildConfig.VERSION_NAME}"

        findViewById<android.widget.Button>(R.id.btn_check_update).setOnClickListener {
            checkForUpdate(showNoUpdateToast = true)
        }

        // เช็คอัปเดตอัตโนมัติทุกครั้งที่เปิดแอพ
        checkForUpdate(showNoUpdateToast = false)
    }

    private fun checkForUpdate(showNoUpdateToast: Boolean) {
        Thread {
            val info = UpdateChecker.fetchLatestRelease(this)
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                if (isFinishing) return@post
                if (info != null && UpdateChecker.isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    showUpdateDialog(info)
                } else if (showNoUpdateToast) {
                    Toast.makeText(this, "เป็นเวอร์ชันล่าสุดอยู่แล้ว", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage("${getString(R.string.update_available_message)}\n\nเวอร์ชันใหม่: ${info.versionName}")
            .setPositiveButton(R.string.update_now) { _, _ -> startUpdate(info) }
            .setNegativeButton(R.string.update_later, null)
            .setCancelable(true)
            .show()
    }

    private fun startUpdate(info: UpdateChecker.UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.update_install_permission_needed)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
        UpdateChecker.downloadAndInstall(this, info.apkDownloadUrl) { success ->
            runOnUiThread {
                if (!success) {
                    Toast.makeText(this, "โหลดอัปเดตไม่สำเร็จ ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
