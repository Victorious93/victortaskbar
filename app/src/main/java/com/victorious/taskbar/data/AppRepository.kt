package com.victorious.taskbar.data

import android.app.ActivityOptions
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.victorious.taskbar.Permissions

data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

/** Process-wide icon bitmap cache so grids and the bar don't re-rasterise drawables every frame. */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(256)
    fun get(app: AppEntry): ImageBitmap =
        cache.get(app.packageName) ?: app.icon.toBitmap(128, 128).asImageBitmap().also { cache.put(app.packageName, it) }
    fun clear() = cache.evictAll()
}

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
        if (limit <= 0 || !Permissions.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val home = homePackage()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName && it.packageName != home }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .take(limit)
            .mapNotNull { entryFor(it) }
    }

    /** Package currently used as the home screen, so it is not listed as a "recent app". */
    private fun homePackage(): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName

    /**
     * Launches [pkg]. SPLIT uses FLAG_ACTIVITY_LAUNCH_ADJACENT (honoured when the system supports
     * split screen from a third-party app). WINDOW passes launch bounds, which Android only applies
     * when freeform windows are enabled (developer options / desktop mode); otherwise it opens normally.
     */
    fun launch(pkg: String, mode: LaunchMode = LaunchMode.NORMAL): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            when (mode) {
                LaunchMode.NORMAL -> context.startActivity(intent)
                LaunchMode.SPLIT -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    context.startActivity(intent)
                }
                LaunchMode.WINDOW -> {
                    val dm = context.resources.displayMetrics
                    val w = (dm.widthPixels * 0.6f).toInt()
                    val h = (dm.heightPixels * 0.6f).toInt()
                    val l = (dm.widthPixels - w) / 2
                    val t = (dm.heightPixels - h) / 3
                    intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    val opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
                    context.startActivity(intent, opts.toBundle())
                }
            }
            true
        } catch (e: Exception) { false }
    }

    fun openAppInfo(pkg: String) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    )

    fun uninstall(pkg: String) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun goHome() = start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    /** Starts an activity from the service context. Returns false instead of crashing. */
    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    /** Opens an Android settings panel (Q+) with a full-settings fallback. */
    fun openPanel(panelAction: String?, fallback: String) {
        if (panelAction != null && Build.VERSION.SDK_INT >= 29 && start(Intent(panelAction))) return
        start(Intent(fallback))
    }
}
