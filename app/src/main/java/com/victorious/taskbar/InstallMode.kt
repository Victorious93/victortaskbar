package com.victorious.taskbar

import android.content.Context
import android.content.pm.ApplicationInfo

/** How this copy of VictorTaskbar is installed. Shown on the main screen; nothing depends on it. */
enum class InstallMode(val label: String) {
    USER("Normal app"),
    SYSTEM("System app"),
    PRIVILEGED("Privileged system app (root module)"),
}

object InstallInfo {
    private val PRIV_APP_DIRS = listOf(
        "/system/priv-app/", "/system/system_ext/priv-app/", "/system_ext/priv-app/",
        "/product/priv-app/", "/system/product/priv-app/", "/vendor/priv-app/",
    )

    fun detect(ctx: Context): InstallMode {
        val info = ctx.applicationInfo
        return classify(info.flags, info.sourceDir, systemSourceDir(ctx))
    }

    /** Pure classification, unit tested. [systemDir] is the factory APK path when the app is an updated system app. */
    fun classify(flags: Int, sourceDir: String?, systemDir: String? = null): InstallMode {
        val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (!isSystem) return InstallMode.USER
        val dir = systemDir ?: sourceDir ?: return InstallMode.SYSTEM
        return if (PRIV_APP_DIRS.any { dir.startsWith(it) }) InstallMode.PRIVILEGED else InstallMode.SYSTEM
    }

    /** Where the root module puts the APK (see module/). */
    const val MODULE_APK = "/system/priv-app/VictorTaskbar/VictorTaskbar.apk"

    /**
     * For an updated system app the live APK is in /data/app. The factory path is not public API,
     * so check for the module's known APK path instead.
     */
    private fun systemSourceDir(ctx: Context): String? {
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) return null
        return try { MODULE_APK.takeIf { java.io.File(it).exists() } } catch (e: SecurityException) { null }
    }
}
