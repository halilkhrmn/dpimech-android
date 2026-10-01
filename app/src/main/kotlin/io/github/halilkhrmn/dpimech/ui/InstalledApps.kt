package io.github.halilkhrmn.dpimech.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(val packageName: String, val label: String, val system: Boolean, val icon: ImageBitmap?)

object InstalledApps {
    /** Apps that can use the network, sorted by name. Slow: call off the main thread. */
    fun load(pm: PackageManager, ownPackage: String): List<InstalledApp> =
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != ownPackage && !it.packageName.startsWith("$ownPackage.") }
            .filter { pm.checkPermission(android.Manifest.permission.INTERNET, it.packageName) == PackageManager.PERMISSION_GRANTED }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

    fun packageNames(pm: PackageManager): Set<String> =
        pm.getInstalledApplications(0).mapTo(HashSet()) { it.packageName }
}
