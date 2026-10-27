package com.victorious.taskbar.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.victorious.taskbar.Permissions

data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    /** All launchable apps, sorted by label. */
    fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun entryFor(pkg: String): AppEntry? = try {
        val info = pm.getApplicationInfo(pkg, 0)
        AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
    } catch (e: Exception) { null }

    /** Recently used apps (last 24h). Requires usage access; returns empty otherwise. */
    fun recentApps(limit: Int = 6): List<AppEntry> {
        if (!Permissions.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .take(limit)
            .mapNotNull { entryFor(it) }
    }

    fun launch(pkg: String) {
        pm.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }
}
