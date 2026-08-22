package com.est.folderwidget

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * เช็คเวอร์ชันล่าสุดจาก GitHub Releases (ฟรี ไม่ต้องมี server ของตัวเอง)
 * ต้องมี Release ที่ผูก .apk ไว้เป็นไฟล์แนบ (asset) — GitHub Actions workflow
 * ในโปรเจกต์นี้จะสร้าง Release + แนบ apk ให้อัตโนมัติทุกครั้งที่ publish release ใหม่บน GitHub
 */
object UpdateChecker {

    data class UpdateInfo(val versionName: String, val apkDownloadUrl: String)

    private fun apiUrl(context: Context): String {
        val owner = context.getString(R.string.github_owner)
        val repo = context.getString(R.string.github_repo)
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    /** เรียกบน background thread เอง ไม่บล็อค UI thread ผู้เรียกต้องจัดการ threading */
    fun fetchLatestRelease(context: Context): UpdateInfo? {
        return try {
            val conn = URL(apiUrl(context)).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            // tag_name มักเป็น "v1.2" -> ตัด v ออกให้เหลือ "1.2" เทียบกับ BuildConfig.VERSION_NAME
            val tag = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            if (tag.isBlank()) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
            if (apkUrl == null) return null
            UpdateInfo(tag, apkUrl)
        } catch (e: Exception) {
            null
        }
    }

    /** true ถ้า remoteVersion ใหม่กว่า currentVersion (เทียบแบบตัวเลขทีละ segment เช่น 1.10 > 1.9) */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remote = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        val current = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(remote.size, current.size)
        for (i in 0 until len) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    /**
     * โหลด apk ผ่าน DownloadManager ของระบบ (มี progress notification ให้เอง)
     * แล้วเรียก onComplete บน main thread เมื่อโหลดเสร็จ พร้อมไฟล์ที่โหลดมา
     */
    fun downloadAndInstall(context: Context, downloadUrl: String, onComplete: (Boolean) -> Unit) {
        val dir = context.getExternalFilesDir("downloads")
        if (dir != null && !dir.exists()) dir.mkdirs()
        val destFile = File(dir, "update.apk")
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(context.getString(R.string.update_downloading))
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // โพลสถานะแบบง่ายๆ (ไม่ต้องใช้ BroadcastReceiver แยกไฟล์ ลดความซับซ้อน)
        val handler = Handler(Looper.getMainLooper())
        val query = DownloadManager.Query().setFilterById(downloadId)
        val checker = object : Runnable {
            override fun run() {
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)
                    cursor.close()
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            installApk(context, destFile)
                            onComplete(true)
                            return
                        }
                        DownloadManager.STATUS_FAILED -> {
                            onComplete(false)
                            return
                        }
                        else -> handler.postDelayed(this, 800)
                    }
                } else {
                    cursor.close()
                    handler.postDelayed(this, 800)
                }
            }
        }
        handler.postDelayed(checker, 800)
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
