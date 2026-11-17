package com.victorious.taskbar

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallInfoTest {
    @Test fun userAppIsUser() =
        assertEquals(InstallMode.USER, InstallInfo.classify(0, "/data/app/~~x/com.victorious.taskbar-1/base.apk"))

    @Test fun moduleInstallIsPrivileged() =
        assertEquals(
            InstallMode.PRIVILEGED,
            InstallInfo.classify(ApplicationInfo.FLAG_SYSTEM, "/system/priv-app/VictorTaskbar/VictorTaskbar.apk"),
        )

    @Test fun plainSystemAppIsSystem() =
        assertEquals(InstallMode.SYSTEM, InstallInfo.classify(ApplicationInfo.FLAG_SYSTEM, "/system/app/VictorTaskbar/VictorTaskbar.apk"))

    @Test fun updatedPrivAppUsesFactoryPath() =
        assertEquals(
            InstallMode.PRIVILEGED,
            InstallInfo.classify(
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                "/data/app/com.victorious.taskbar-2/base.apk",
                "/system/priv-app/VictorTaskbar/VictorTaskbar.apk",
            ),
        )
}
