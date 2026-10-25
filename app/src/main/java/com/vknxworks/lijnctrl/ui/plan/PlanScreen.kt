package com.vknxworks.lijnctrl.ui.plan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.lines.LineInfo
import com.vknxworks.lijnctrl.data.location.Locator
import com.vknxworks.lijnctrl.data.plan.Journey
import com.vknxworks.lijnctrl.data.plan.JourneyLeg
import com.vknxworks.lijnctrl.data.plan.PlanPoint
import com.vknxworks.lijnctrl.str
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.PillButton
import com.vknxworks.lijnctrl.ui.components.TabTitle
import com.vknxworks.lijnctrl.ui.components.clockTime
import com.vknxworks.lijnctrl.ui.components.dayTime
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.nav.JourneyRoute
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nav.PickPlaceRoute
import com.vknxworks.lijnctrl.ui.nav.SettingsRoute
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The Plan tab: a route from A to B, like De Lijn's own route planner. */
@Composable
fun PlanScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val s = PlanState
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val history by c.planHistory.entries.collectAsStateWithLifecycle()

    fun plan() {
        val from = s.from ?: return
        val to = s.to ?: return
        if (s.planning) return
        s.planning = true
        s.error = null
        scope.launch {
            try {
                suspend fun resolve(p: PlanPoint): PlanPoint? {
                    if (!p.isMyLocation) return p
                    val loc = Locator.current(context) ?: return null
                    return p.copy(latitude = loc.latitude, longitude = loc.longitude)
                }
                val a = resolve(from)
                val b = resolve(to)
                if (a == null || b == null) {
                    s.error = str(R.string.no_location_fix)
                    s.results = null
                    return@launch
                }
                val time = if (s.mode == TimeMode.NOW) Instant.now() else s.time ?: Instant.now()
                s.results = c.planner.plan(a, b, time, s.mode == TimeMode.ARRIVE_BY, settings.transitousRoutes)
                // Remembers from and to as you chose them ("my location" stays "my location", not where you were).
                if (settings.planHistory) runCatching { c.planHistory.add(from, to) }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                s.error = e.friendlyMessage()
                s.results = null
            } finally {
                s.planning = false
            }
        }
    }

    // "My location" needs the location permission; asked once, when you plan from or to it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) plan() else s.error = str(R.string.location_needed_for_my_location)
    }
    fun planChecked() {
        val needsLocation = s.from?.isMyLocation == true || s.to?.isMyLocation == true
        if (needsLocation && !Locator.hasPermission(context)) {
            AppLock.expectReturn()
            permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } else plan()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_plan))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp + LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            EndRow(Icons.Outlined.TripOrigin, stringResource(R.string.from), s.from) { nav.navigate(PickPlaceRoute(forFrom = true)) }
                            HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            EndRow(Icons.Outlined.Flag, stringResource(R.string.to), s.to) { nav.navigate(PickPlaceRoute(forFrom = false)) }
                        }
                        IconButton(onClick = {
                            val f = s.from
                            s.from = s.to
                            s.to = f
                            s.results = null
                        }) { Icon(Icons.Outlined.SwapVert, stringResource(R.string.swap)) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeMode.entries.forEach { m ->
                        FilterChip(
                            selected = s.mode == m,
                            onClick = {
                                s.mode = m
                                if (m != TimeMode.NOW && s.time == null) s.time = Instant.now()
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (m) {
                                            TimeMode.NOW -> R.string.leave_now
                                            TimeMode.DEPART_AT -> R.string.leave_at
                                            TimeMode.ARRIVE_BY -> R.string.arrive_by
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            if (s.mode != TimeMode.NOW) {
                item {
                    val t = s.time ?: Instant.now()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate = true }, shape = CircleShape) {
                            Text(dayLabel(t.atZone(BRUSSELS).toLocalDate()))
                        }
                        OutlinedButton(onClick = { pickTime = true }, shape = CircleShape) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(clockTime(t))
                        }
                    }
                }
            }
            item {
                PillButton(
                    if (s.planning) stringResource(R.string.planning) else stringResource(R.string.plan_route),
                    onClick = ::planChecked,
                    enabled = s.from != null && s.to != null && !s.planning,
                    icon = Icons.Outlined.Directions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(if (settings.transitousRoutes) R.string.planner_transitous_note else R.string.planner_builtin_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { nav.navigate(SettingsRoute) },
                )
            }
            val results = s.results
            val err = s.error
            when {
                err != null -> item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                results == null -> if (!s.planning && settings.planHistory && history.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.recent_routes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { c.planHistory.clear() } }) { Text(stringResource(R.string.clear_history)) }
                        }
                    }
                    item {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                history.forEachIndexed { i, h ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    HistoryRow(
                                        h,
                                        onClick = {
                                            s.from = h.from.toPoint()
                                            s.to = h.to.toPoint()
                                            s.results = null
                                            planChecked()
                                        },
                                        onRemove = { scope.launch { c.planHistory.remove(h) } },
                                    )
                                }
                            }
                        }
                    }
                }
                results.isEmpty() -> item {
                    MessageView(
                        Icons.Outlined.Directions,
                        stringResource(R.string.no_routes_found),
                        stringResource(if (settings.transitousRoutes) R.string.no_routes_found_body_transitous else R.string.no_routes_found_body),
                        Modifier.height(300.dp),
                    )
                }
                else -> {
                    if (results.any { j -> j.legs.any { it.path.size >= 2 } }) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pluralStringResource(R.plurals.routes_found, results.size, results.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(onClick = { nav.navigate(com.vknxworks.lijnctrl.ui.nav.RouteMapRoute(0)) }, shape = CircleShape) {
                                    Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.show_on_map))
                                }
                            }
                        }
                    }
                    itemsIndexed(results, key = { i, j -> "$i-${j.signature}" }) { i, j ->
                    JourneyCard(j) { nav.navigate(JourneyRoute(i)) }
                    }
                }
            }
        }
    }

    if (pickDate) {
        val initial = (s.time ?: Instant.now()).atZone(BRUSSELS).toLocalDate()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val clock = (s.time ?: Instant.now()).atZone(BRUSSELS).toLocalTime()
                        s.time = day.atTime(clock).atZone(BRUSSELS).toInstant()
                        s.results = null
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.lock_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val now = (s.time ?: Instant.now()).atZone(BRUSSELS)
        val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val day = (s.time ?: Instant.now()).atZone(BRUSSELS).toLocalDate()
                    s.time = day.atTime(LocalTime.of(state.hour, state.minute)).atZone(BRUSSELS).toInstant()
                    s.results = null
                    pickTime = false
                }) { Text(stringResource(R.string.lock_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

/** A route from the history: from and to, and when you last planned it. Tap to plan it again. */
@Composable
private fun HistoryRow(h: com.vknxworks.lijnctrl.data.plan.PlanHistoryEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    val myLocation = stringResource(R.string.my_location)
    fun label(p: com.vknxworks.lijnctrl.data.plan.SavedPoint) = if (p.isMyLocation) myLocation else p.name
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label(h.from), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "→ ${label(h.to)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dayTime(Instant.ofEpochMilli(h.plannedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.remove_from_history), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now(BRUSSELS)
    return when (day) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> com.vknxworks.lijnctrl.ui.components.day(day.atStartOfDay(BRUSSELS).toInstant())
    }
}

@Composable
private fun EndRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, point: PlanPoint?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                point?.name ?: stringResource(R.string.choose_a_place),
                style = MaterialTheme.typography.bodyLarge,
                color = if (point == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The badge for a ride: De Lijn's colours when the line is known, otherwise the planner's. */
fun legBadgeInfo(leg: JourneyLeg, lines: Map<String, LineInfo>): LineInfo? {
    leg.line?.let { k -> lines[k.toString()]?.let { return it } }
    val text = leg.badge ?: return null
    return LineInfo(entity = "", line = "", publicNumber = text, foreground = leg.badgeForeground, background = leg.badgeBackground)
}

/** A route in the results: times, length, changes, and the rides in order. */
@Composable
fun JourneyCard(j: Journey, onClick: (() -> Unit)?) {
    val lines = rememberLineInfo(j.legs.mapNotNull { it.line })
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${dayTime(j.start)}  →  ${clockTime(j.end)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(durationText(j.minutes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                j.legs.forEachIndexed { i, leg ->
                    if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (leg.isWalk) {
                        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${leg.minutes}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Box(Modifier.align(Alignment.CenterVertically)) { LineBadge(legBadgeInfo(leg, lines), leg.badge ?: "?", minWidth = 36.dp) }
                    }
                }
            }
            val firstRide = j.legs.firstOrNull { !it.isWalk }
            Text(
                listOfNotNull(
                    if (j.walkOnly) stringResource(R.string.walk_only) else pluralStringResource(R.plurals.changes, j.transfers, j.transfers),
                    firstRide?.let { stringResource(R.string.board_at_x_y, it.fromName, clockTime(it.start)) },
                    if (j.legs.any { it.live }) stringResource(R.string.live) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun durationText(minutes: Long): String =
    if (minutes < 60) stringResource(R.string.x_min_short, minutes.toInt())
    else stringResource(R.string.x_h_y_min, (minutes / 60).toInt(), (minutes % 60).toInt())
