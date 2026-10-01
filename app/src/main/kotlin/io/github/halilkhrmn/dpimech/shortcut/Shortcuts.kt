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
import io.github.halilkhrmn.dpimech.ui.MainActivity

/**
 * Launcher shortcuts: "turn on this profile, then open its app" (e.g. Discord). The selected
 * profile and the next ones are offered on a long press of the app icon; any profile can be
 * pinned to the home screen from the profile editor.
 */
object Shortcuts {
    const val EXTRA_PROFILE = "shortcut_profile"
    const val EXTRA_OPEN = "shortcut_open"
    const val ACTION_STOP = "io.github.halilkhrmn.dpimech.shortcut.STOP"
    private const val ID_OFF = "action-off"
    private const val ID_TEST = "action-test"
    private const val ID_LOGS = "action-logs"

    /**
     * Long-press menu of the app icon: "Turn off" while the bypass runs, the saved profiles (the
     * selected one first), "Strategy test" and, when there is room, "Logs".
     */
    fun publish(context: Context, saved: SavedProfiles, running: Boolean = false) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceIn(1, 4)
        val ordered = listOfNotNull(saved.selected) + saved.profiles.filter { it.id != saved.selected?.id }
        val head = if (running) listOf(off(context)) else emptyList()
        val test = screen(context, ID_TEST, MainActivity.SCREEN_TEST, R.string.shortcut_test, R.string.shortcut_test_long, R.drawable.ic_shortcut_test)
        val profiles = ordered.take((max - head.size - 1).coerceAtLeast(0)).map { info(context, it) }
        val logs = screen(context, ID_LOGS, MainActivity.SCREEN_LOGS, R.string.shortcut_logs, R.string.shortcut_logs_long, R.drawable.ic_shortcut_logs)
        val all = (head + profiles + test + logs).take(max)
        val list = all.mapIndexed { i, b -> b.setRank(i).build() }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    private fun off(context: Context) = ShortcutInfoCompat.Builder(context, ID_OFF)
        .setShortLabel(context.getString(R.string.shortcut_off))
        .setLongLabel(context.getString(R.string.shortcut_off_long))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_off))
        .setIntent(Intent(context, ShortcutActivity::class.java).setAction(ACTION_STOP))

    private fun screen(context: Context, id: String, screen: String, short: Int, long: Int, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(short))
            .setLongLabel(context.getString(long))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_SCREEN, screen))

    /** Tells the launcher a shortcut was used, so it can rank it (profile or "Turn off"). */
    fun reportUsed(context: Context, intent: Intent) {
        val id = if (intent.action == ACTION_STOP) ID_OFF else intent.getStringExtra(EXTRA_PROFILE)?.let { "profile-$it" } ?: return
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
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
