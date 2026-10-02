package io.github.halilkhrmn.dpimech.ui

import io.github.halilkhrmn.dpimech.core.DomainPack
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.NetworkInfo
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import io.github.halilkhrmn.dpimech.core.TrafficStats
import io.github.halilkhrmn.dpimech.core.Transport
import io.github.halilkhrmn.dpimech.engine.EngineState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    saved: SavedProfiles,
    engine: EngineState,
    onSelect: (String) -> Unit,
    onToggle: (Profile) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onWizard: () -> Unit,
    bottomPadding: PaddingValues,
    network: NetworkInfo? = null,
    autoStrategy: Boolean = true,
    stats: TrafficStats = TrafficStats(),
    encryptedDns: Boolean = true,
    blockQuic: Boolean = false,
    onPing: () -> Unit = {},
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    profile = saved.selected,
                    engine = engine,
                    network = network,
                    autoStrategy = autoStrategy,
                    stats = stats,
                    // While running, the chips show what this session actually uses.
                    encryptedDns = if (engine is EngineState.Running) stats.encryptedDns else encryptedDns,
                    blockQuic = if (engine is EngineState.Running) stats.blockQuic else blockQuic,
                    onToggle = onToggle,
                    onWizard = onWizard,
                    onEdit = onEdit,
                )
            }
            if (engine is EngineState.Running) item { TrafficCard(stats, onPing) }
            item { ProfilesCard(saved, onSelect, onEdit, onNew) }
            item {
                Text(
                    stringResource(R.string.notice_not_vpn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Power button, state, profile and the network in one card; tinted green while on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    profile: Profile?,
    engine: EngineState,
    network: NetworkInfo?,
    autoStrategy: Boolean,
    stats: TrafficStats,
    encryptedDns: Boolean,
    blockQuic: Boolean,
    onToggle: (Profile) -> Unit,
    onWizard: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val on = engine is EngineState.Running
    val busy = engine is EngineState.Starting
    val failed = engine is EngineState.Failed
    val surface by animateColorAsState(
        when {
            on -> OnGreen.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surface)
            failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "hero",
    )
    Surface(shape = RoundedCornerShape(32.dp), color = surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PowerButton(on, busy, failed) { if (profile == null && !on) onWizard() else profile?.let(onToggle) }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    when {
                        on -> R.string.home_state_on
                        busy -> R.string.status_starting
                        failed -> R.string.status_failed
                        else -> R.string.home_state_off
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (on) Uptime(stats.since)
            Text(
                when (engine) {
                    is EngineState.Running -> stringResource(R.string.status_running_hint)
                    is EngineState.Starting -> ""
                    is EngineState.Failed -> engine.message
                    EngineState.Stopped -> stringResource(if (profile == null) R.string.status_no_profile else R.string.status_off_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (profile == null && !on) {
                Button(onClick = onWizard, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.easy_button)) }
            }
            if (engine is EngineState.Running && engine.autoTesting) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(stringResource(R.string.auto_testing), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }

            if (profile != null) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ActiveProfile(profile, engine, network, autoStrategy) { onEdit(profile.id) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                NetworkChip(network)
                if (encryptedDns) InfoChip(Icons.Default.Lock, stringResource(R.string.chip_doh))
                if (blockQuic) InfoChip(Icons.Default.Speed, stringResource(R.string.chip_quic))
            }
        }
    }
}

/** The one big switch: colour, icon and a word tell at a glance whether the bypass is on. */
@Composable
private fun PowerButton(on: Boolean, busy: Boolean, failed: Boolean, onClick: () -> Unit) {
    // Green for on regardless of the wallpaper colours: the state must read at a glance.
    val container by animateColorAsState(
        when {
            on -> OnGreen
            failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "power",
    )
    val size by animateDpAsState(if (on) 168.dp else 156.dp, label = "size")
    val word = stringResource(
        when {
            on -> R.string.power_on
            busy -> R.string.power_starting
            else -> R.string.power_off
        },
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = if (on || failed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (on) 6.dp else 0.dp,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = word }
            .clickable(enabled = !busy, role = Role.Switch, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(72.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(64.dp))
                    Text(word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** "12:04" since the bypass started, ticking. */
@Composable
private fun Uptime(since: Long) {
    if (since <= 0) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val s = ((now - since) / 1000).coerceAtLeast(0)
    val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActiveProfile(p: Profile, engine: EngineState, network: NetworkInfo?, autoStrategy: Boolean, onEdit: () -> Unit) {
    val strategy = (engine as? EngineState.Running)?.strategyName?.ifEmpty { null }
    val remembered = network?.networkKey?.let { p.perNetwork[it] }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Text(profileScope(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    strategy != null -> stringResource(R.string.profile_strategy_line, strategy)
                    remembered != null -> stringResource(R.string.net_remembered, remembered.name)
                    network != null && autoStrategy -> stringResource(R.string.net_auto)
                    else -> stringResource(R.string.profile_strategy_line, p.strategy.name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit)) }
    }
}

/** Which network the phone is on: the per-network strategy memory is keyed by the provider. */
@Composable
private fun NetworkChip(network: NetworkInfo?) {
    val kind = when (network?.transport) {
        Transport.WIFI -> stringResource(R.string.net_wifi)
        Transport.CELLULAR -> stringResource(R.string.net_mobile)
        Transport.ETHERNET -> "Ethernet"
        Transport.OTHER -> stringResource(R.string.net_other)
        null -> stringResource(R.string.net_none)
    }
    val icon = when (network?.transport) {
        Transport.WIFI -> Icons.Default.Wifi
        Transport.CELLULAR -> Icons.Default.SignalCellularAlt
        null -> Icons.Default.CloudOff
        else -> Icons.Default.Lan
    }
    val provider = network?.providerName ?: if (network != null && network.networkKey == null) stringResource(R.string.net_looking_up) else null
    InfoChip(icon, listOfNotNull(kind, provider).joinToString(" · "))
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/**
 * Live traffic of the bypassed apps, the last minute: download as a filled area, upload as a
 * dashed line (so the two differ without colour too). Touch the chart to read a second.
 */
@Composable
private fun TrafficCard(stats: TrafficStats, onPing: () -> Unit) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    var picked by remember { mutableIntStateOf(-1) }
    val n = stats.down.size
    val index = if (picked in 0 until n) picked else n - 1
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            PingRow(stats, onPing)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.home_traffic), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Legend(downColor, dashed = false, stringResource(R.string.home_download), TrafficStats.bytes(stats.down.getOrElse(index) { 0 }) + "/s")
                Legend(upColor, dashed = true, stringResource(R.string.home_upload), TrafficStats.bytes(stats.up.getOrElse(index) { 0 }) + "/s")
            }
            AnimatedVisibility(picked in 0 until n) {
                Text(
                    stringResource(R.string.home_seconds_ago, n - 1 - index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrafficChart(
                stats, downColor, upColor, index.takeIf { picked >= 0 },
                onPick = { picked = it },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp),
            )
            // Same height for all three, whatever their text.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.home_total), "↓ ${TrafficStats.bytes(stats.downTotal)}\n↑ ${TrafficStats.bytes(stats.upTotal)}", Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.home_dns),
                    if (stats.encryptedDns) "${stats.dnsEncrypted}/${stats.dnsQueries}" else "—",
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(if (stats.blockQuic) R.string.home_quic else R.string.home_restarts),
                    (if (stats.blockQuic) stats.quicBlocked.toInt() else stats.restarts).toString(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, dashed: Boolean, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(8.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun TrafficChart(
    stats: TrafficStats,
    downColor: Color,
    upColor: Color,
    picked: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val description = stringResource(R.string.home_chart)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val slots = TrafficStats.HISTORY
    fun indexAt(x: Float, width: Float): Int {
        val first = slots - stats.down.size
        return ((x / width * (slots - 1)).toInt() - first).coerceIn(0, (stats.down.size - 1).coerceAtLeast(0))
    }
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(stats.down.size) {
                detectTapGestures(onPress = { onPick(indexAt(it.x, size.width.toFloat())); tryAwaitRelease(); onPick(-1) })
            }
            .pointerInput(stats.down.size) {
                detectDragGestures(
                    onDragStart = { onPick(indexAt(it.x, size.width.toFloat())) },
                    onDragEnd = { onPick(-1) },
                    onDragCancel = { onPick(-1) },
                ) { change, _ -> onPick(indexAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val w = size.width
        val h = size.height
        // Recessive grid: baseline and a middle line.
        drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
        val max = ((stats.down + stats.up).maxOrNull() ?: 0L).coerceAtLeast(1024L).toFloat() * 1.15f
        fun x(i: Int, count: Int) = w * (slots - count + i) / (slots - 1).toFloat()
        fun y(v: Long) = h - (v / max) * h
        fun line(values: List<Long>) = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i, values.size), y(v)) else lineTo(x(i, values.size), y(v)) }
        }
        val down = stats.down
        if (down.size >= 2) {
            val area = line(down).apply {
                lineTo(x(down.size - 1, down.size), h)
                lineTo(x(0, down.size), h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(downColor.copy(alpha = 0.35f), downColor.copy(alpha = 0.04f))))
            drawPath(line(down), downColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (stats.up.size >= 2) {
            drawPath(
                line(stats.up), upColor,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        val i = picked ?: (down.size - 1)
        if (i in down.indices) {
            val px = x(i, down.size)
            if (picked != null) drawLine(grid, Offset(px, 0f), Offset(px, h), strokeWidth = 1.dp.toPx())
            // Markers with a surface ring so they stay readable over the lines.
            for ((v, c) in listOf(down[i] to downColor, stats.up.getOrElse(i) { 0L } to upColor)) {
                drawCircle(surface, 6.dp.toPx(), Offset(px, y(v)))
                drawCircle(c, 4.dp.toPx(), Offset(px, y(v)))
            }
        }
    }
}

/** "Average ping 84 ms · 4 of 4 sites open · 2 min ago" with a refresh button. */
@Composable
private fun PingRow(stats: TrafficStats, onPing: () -> Unit) {
    val ping = stats.ping
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ping?.at) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.NetworkPing, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(R.string.home_ping), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when (val ms = ping?.averageMs) {
                    null -> stringResource(if (ping == null) R.string.home_ping_measuring else R.string.home_ping_none)
                    else -> stringResource(R.string.home_ping_value, ms)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (ping != null && ping.total > 0) {
                val minutes = ((now - ping.at) / 60_000).coerceAtLeast(0)
                Text(
                    stringResource(R.string.home_ping_sites, ping.ok, ping.total) + " · " +
                        if (minutes < 1) stringResource(R.string.home_ping_just_now) else stringResource(R.string.home_ping_ago, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ping.ok < ping.total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (stats.pinging) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onPing) { Icon(Icons.Default.Refresh, stringResource(R.string.home_ping_refresh)) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfilesCard(saved: SavedProfiles, onSelect: (String) -> Unit, onEdit: (String) -> Unit, onNew: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.profile_new), Modifier.padding(start = 6.dp))
            }
        }
        if (saved.profiles.isEmpty()) return
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                saved.profiles.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val selected = p.id == saved.selected?.id
                    ListItem(
                        leadingContent = {
                            Icon(
                                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        headlineContent = { Text(p.name, fontWeight = if (selected) FontWeight.SemiBold else null) },
                        supportingContent = { Text(profileScope(p)) },
                        trailingContent = {
                            IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.RadioButton) { onSelect(p.id) },
                    )
                }
            }
        }
    }
}

private val OnGreen = Color(0xFF1E8E3E)

/** A site pack's name in the app's language (the shared file has a few translated names). */
@Composable
fun DomainPack.label(): String = displayName(LocalConfiguration.current.locales[0].language)

/** "All apps · 4 sites" or "2 apps · 1 site". */
@Composable
fun profileScope(p: Profile): String {
    val apps = if (p.appMode == AppMode.ALL_EXCEPT) {
        if (p.apps.isEmpty()) stringResource(R.string.scope_all_apps) else stringResource(R.string.scope_all_apps_except, p.apps.size)
    } else {
        stringResource(R.string.scope_apps, p.apps.size)
    }
    val sites = if (p.domainFilter) stringResource(R.string.scope_sites, p.packs.size + p.extraDomains.size) else stringResource(R.string.scope_all_sites)
    return "$apps · $sites"
}
