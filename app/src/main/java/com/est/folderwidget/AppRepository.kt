package com.est.folderwidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** ดึงรายชื่อแอพทั้งหมดในเครื่องที่เปิดได้ (มี launcher icon) */
object AppRepository {

    fun getLaunchableApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolveInfos
            .map { info ->
                AppItem(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun loadIcon(context: Context, packageName: String) =
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    fun launch(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
