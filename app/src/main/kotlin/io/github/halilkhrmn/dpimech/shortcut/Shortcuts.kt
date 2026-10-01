package io.github.halilkhrmn.dpimech.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles

/**
 * Launcher shortcuts: "turn on this profile, then open its app" (e.g. Discord). The selected
 * profile and the next ones are offered on a long press of the app icon; any profile can be
 * pinned to the home screen from the profile editor.
 */
object Shortcuts {
    const val EXTRA_PROFILE = "shortcut_profile"
    const val EXTRA_OPEN = "shortcut_open"

    /** Long-press shortcuts follow the saved profiles (the selected one first). */
    fun publish(context: Context, saved: SavedProfiles) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
        val ordered = listOfNotNull(saved.selected) + saved.profiles.filter { it.id != saved.selected?.id }
        val list = ordered.take(max).mapIndexed { i, p -> info(context, p).setRank(i).build() }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    fun canPin(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, profile: Profile) {
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, info(context, profile).build(), null) }
    }

    private fun info(context: Context, p: Profile): ShortcutInfoCompat.Builder {
        val pm = context.packageManager
        val open = p.appToOpen(launchable(pm, p))
        val appLabel = open?.let { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull() }
        val intent = Intent(context, ShortcutActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PROFILE, p.id)
            .apply { open?.let { putExtra(EXTRA_OPEN, it) } }
        return ShortcutInfoCompat.Builder(context, "profile-${p.id}")
            .setShortLabel(context.getString(R.string.shortcut_short, p.name).take(25))
            .setLongLabel(
                appLabel?.let { context.getString(R.string.shortcut_long_open, p.name, it) }
                    ?: context.getString(R.string.shortcut_long, p.name),
            )
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
    }

    /** Only the packages the profile could open are checked, not every installed app. */
    private fun launchable(pm: PackageManager, p: Profile): Set<String> =
        (p.apps + p.packs.flatMap { io.github.halilkhrmn.dpimech.core.DomainPack.byId(it)?.packages.orEmpty() })
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .toSet()
}
