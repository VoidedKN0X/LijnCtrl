package com.vknxworks.lijnctrl.ui.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.ApiTrip
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.parseLocalTime
import com.vknxworks.lijnctrl.ui.components.ErrorView
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.UpdatedLine
import com.vknxworks.lijnctrl.ui.components.clockTime
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.stops.REFRESH_MS
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/** One stop of a trip, as shown on the trip page. */
private data class TripStop(
    val number: String,
    val name: String,
    val town: String?,
    val scheduled: Instant?,
    val predicted: Instant?,
    val cancelled: Boolean,
) {
    val time: Instant? get() = predicted ?: scheduled
}

/**
 * Every stop of one bus or tram run, with times: live predictions while the trip is on the road, the
 * timetable otherwise. Stops already passed are dimmed; the stop you came from is highlighted.
 */
@Composable
fun TripScreen(entity: String, line: String, direction: String, trip: String, date: String, fromStop: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val lineKey = remember(entity, line) { LineKey(entity, line) }
    val serviceDate = remember(date) { runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now(BRUSSELS) } }
    val info = rememberLineInfo(listOf(lineKey))[lineKey.toString()]
    val now by rememberNow()

    var planned by remember { mutableStateOf<ApiTrip?>(null) }
    var names by remember { mutableStateOf<Map<String, ApiStop>>(emptyMap()) }
    var live by remember { mutableStateOf<ApiTrip?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updated by remember { mutableStateOf<Instant?>(null) }
    var attempt by remember { mutableStateOf(0) }

    // The trip itself and the names of its stops: once.
    LaunchedEffect(attempt) {
        error = null
        runCatching {
            coroutineScope {
                val t = async { c.api.trip(lineKey, direction, trip, serviceDate).trips.firstOrNull() }
                val route = async { runCatching { c.api.lineStops(lineKey, direction) }.getOrDefault(emptyList()) }
                val tripResult = t.await()
                val known = route.await().associateBy { it.number }
                // Stops of this run that aren't on the line's usual route: looked up by number.
                val missing = tripResult?.passages?.mapNotNull { it.stopNumber }?.filter { it !in known }.orEmpty()
                val extra = if (missing.isEmpty()) emptyMap() else runCatching {
                    missing.chunked(20).flatMap { group -> c.api.stops(group.map { StopKey(entity, it) }) }.associateBy { it.number }
                }.getOrDefault(emptyMap())
                planned = tripResult
                names = known + extra
            }
        }.onFailure { error = it.friendlyMessage() }
    }

    // Live predictions while the trip runs today (or after midnight on yesterday's service day).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lineKey, direction, trip) {
        val today = LocalDate.now(BRUSSELS)
        if (serviceDate != today && serviceDate != today.minusDays(1)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { c.api.lineRealtime(lineKey, direction) }.onSuccess { r ->
                    live = r.trips.firstOrNull { it.trip == trip }
                    updated = Instant.now()
                }
                delay(REFRESH_MS)
            }
        }
    }

    val stops = remember(planned, live, names) {
        val livePassages = live?.passages.orEmpty().associateBy { it.stopNumber }
        planned?.passages.orEmpty().mapNotNull { p ->
            val number = p.stopNumber ?: return@mapNotNull null
            val l = livePassages[number]
            val stop = names[number]
            TripStop(
                number = number,
                name = stop?.name ?: stop?.longName ?: number,
                town = stop?.municipality,
                scheduled = parseLocalTime(p.scheduled),
                predicted = parseLocalTime(l?.realtime),
                cancelled = l?.statuses?.any { it.equals("GESCHRAPT", true) } == true,
            )
        }
    }
    val destination = planned?.destination ?: planned?.placeDestination ?: live?.destination
    val listState = rememberLazyListState()
    LaunchedEffect(stops.isNotEmpty()) {
        val i = stops.indexOfFirst { it.number == fromStop }
        if (i > 2) listState.scrollToItem(i - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(destination ?: stringResource(R.string.trip)) },
    ) { padding ->
        val err = error
        when {
            err != null -> ErrorView(err, Modifier.padding(padding)) { attempt++ }
            planned == null -> LoadingView(Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.line(entity, line, direction) }.padding(horizontal = Gutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineBadge(info, line, large = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.description ?: stringResource(R.string.line_x, info?.publicNumber ?: line), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.trip_x, trip),
                                    if (serviceDate != LocalDate.now(BRUSSELS)) com.vknxworks.lijnctrl.ui.components.day(serviceDate.atStartOfDay(BRUSSELS).toInstant()) else null,
                                    if (live != null) stringResource(R.string.on_the_road) else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(stops, key = { i, s -> "$i-${s.number}" }) { i, s ->
                    TripStopRow(
                        s,
                        first = i == 0,
                        last = i == stops.lastIndex,
                        passed = s.time?.isBefore(now.minusSeconds(30)) == true,
                        mine = s.number == fromStop,
                        onClick = { nav.stop(entity, s.number, s.name) },
                    )
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 12.dp)) {
                        UpdatedLine(updated)
                        Text(
                            stringResource(if (live != null) R.string.trip_live_note else R.string.trip_timetable_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStopRow(s: TripStop, first: Boolean, last: Boolean, passed: Boolean, mine: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.primary.copy(alpha = if (passed) 0.35f else 0.8f)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = Gutter).alpha(if (passed && !mine) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The route line with a dot for every stop; your stop gets a bigger one.
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = size.height / 2
            if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, y), strokeWidth = 4.dp.toPx())
            if (!last) drawLine(lineColor, Offset(x, y), Offset(x, size.height), strokeWidth = 4.dp.toPx())
            drawCircle(if (mine) cs.primary else cs.surface, radius = if (mine) 8.dp.toPx() else 5.dp.toPx(), center = Offset(x, y))
            drawCircle(lineColor, radius = if (mine) 8.dp.toPx() else 5.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                s.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) cs.primary else cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (s.cancelled) TextDecoration.LineThrough else null,
            )
            val sub = listOfNotNull(s.town, if (mine) stringResource(R.string.your_stop) else null).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val t = s.time
            if (s.cancelled) {
                Text(stringResource(R.string.cancelled), color = cs.error, style = MaterialTheme.typography.labelLarge)
            } else if (t != null) {
                Text(
                    clockTime(t),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (s.predicted != null) cs.primary else cs.onSurface,
                )
                val sched = s.scheduled
                val pred = s.predicted
                if (pred != null && sched != null) {
                    val delay = Math.round((pred.toEpochMilli() - sched.toEpochMilli()) / 60_000.0)
                    if (delay != 0L) {
                        Text(
                            if (delay > 0) stringResource(R.string.x_min_late, delay.toInt()) else stringResource(R.string.x_min_early, (-delay).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delay >= 3) cs.error else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (last) Spacer(Modifier.height(4.dp))
}
