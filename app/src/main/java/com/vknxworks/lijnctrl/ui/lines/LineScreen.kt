package com.vknxworks.lijnctrl.ui.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiDisruption
import com.vknxworks.lijnctrl.data.api.ApiLineDirection
import com.vknxworks.lijnctrl.data.api.ApiLineDirectionSchedule
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.localized
import com.vknxworks.lijnctrl.data.api.parseLocalTime
import com.vknxworks.lijnctrl.data.favourites.SavedLine
import com.vknxworks.lijnctrl.ui.components.DisruptionCard
import com.vknxworks.lijnctrl.ui.components.ErrorView
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.stops.REFRESH_MS
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** "Flanders Expo - Kouter - Moscou" → "Moscou": where a direction ends. */
fun directionEnd(description: String?): String? =
    description?.split(" - ", "- ", " -")?.lastOrNull()?.trim()?.takeIf { it.isNotBlank() }

/** A line: its route in both directions, stop by stop, where its buses or trams are now, and disruptions. */
@Composable
fun LineScreen(entity: String, line: String, initialDirection: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val key = remember(entity, line) { LineKey(entity, line) }
    val info = rememberLineInfo(listOf(key))[key.toString()]
    val savedLines by c.favourites.lines.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val isSaved = savedLines.any { it.key == key }
    val now by rememberNow()

    var directions by remember { mutableStateOf<List<ApiLineDirection>?>(null) }
    var selected by rememberSaveable { mutableStateOf(initialDirection) }
    var stops by remember { mutableStateOf<List<ApiStop>?>(null) }
    var live by remember { mutableStateOf<ApiLineDirectionSchedule?>(null) }
    var disruptions by remember { mutableStateOf<List<ApiDisruption>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(key, attempt) {
        error = null
        runCatching { c.api.lineDirections(key) }
            .onSuccess { list ->
                directions = list
                if (selected == null || list.none { it.direction == selected }) selected = list.firstOrNull()?.direction
            }
            .onFailure { error = it.friendlyMessage() }
    }

    val direction = selected
    LaunchedEffect(key, direction, attempt) {
        if (direction == null) return@LaunchedEffect
        stops = null
        live = null
        disruptions = emptyList()
        runCatching { c.api.lineStops(key, direction) }
            .onSuccess { stops = it }
            .onFailure { error = it.friendlyMessage() }
        disruptions = runCatching { c.api.lineDisruptions(key, direction) }.getOrDefault(emptyList())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, direction) {
        if (direction == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { c.api.lineRealtime(key, direction) }.onSuccess { live = it }
                delay(REFRESH_MS)
            }
        }
    }

    // Where each vehicle on the road is heading next: its first stop still to come, and in how long.
    val approaching = remember(live, now) {
        val result = mutableMapOf<String, Instant>()
        live?.trips.orEmpty().forEach { t ->
            val next = t.passages.firstOrNull { p ->
                val time = parseLocalTime(p.realtime) ?: parseLocalTime(p.scheduled)
                time != null && time.isAfter(now)
            }
            val n = next ?: return@forEach
            val stop = n.stopNumber ?: return@forEach
            val time = parseLocalTime(n.realtime) ?: parseLocalTime(n.scheduled) ?: return@forEach
            val current = result[stop]
            if (current == null || time.isBefore(current)) result[stop] = time
        }
        result
    }
    val onRoad = live?.trips?.count { t -> t.passages.any { p -> (parseLocalTime(p.realtime) ?: parseLocalTime(p.scheduled))?.isAfter(now) == true } } ?: 0

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.line_x, info?.publicNumber ?: line)) {
                IconButton(onClick = {
                    scope.launch { c.favourites.toggleLine(SavedLine(entity, line, info?.publicNumber ?: line, info?.description)) }
                }) {
                    Icon(
                        if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        stringResource(if (isSaved) R.string.remove_line_from_stops else R.string.add_line_to_stops),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        val err = error
        when {
            err != null && (directions == null || stops == null) -> ErrorView(err, Modifier.padding(padding)) { attempt++ }
            directions == null -> LoadingView(Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(info, line, large = true, minWidth = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    stringResource(if (info?.isTram == true) R.string.tram else R.string.bus),
                                    if (live != null) androidx.compose.ui.res.pluralStringResource(R.plurals.vehicles_on_the_road, onRoad, onRoad) else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val dirs = directions.orEmpty()
                if (dirs.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Gutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            items(dirs, key = { it.direction.orEmpty() }) { d ->
                                FilterChip(
                                    selected = d.direction == direction,
                                    onClick = { selected = d.direction },
                                    label = { Text(stringResource(R.string.to_x, directionEnd(d.description) ?: d.direction.orEmpty()), maxLines = 1) },
                                )
                            }
                        }
                    }
                }
                val localized = disruptions.map { it.localized(settings.messageLanguage.key) }
                items(localized) { d -> DisruptionCard(d, emptyMap(), Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }
                val list = stops
                if (list == null) {
                    item { LoadingView(Modifier.height(240.dp)) }
                } else {
                    itemsIndexed(list, key = { i, s -> "$i-${s.number}" }) { i, s ->
                        RouteStopRow(
                            stop = s,
                            first = i == 0,
                            last = i == list.lastIndex,
                            arriving = approaching[s.number],
                            now = now,
                            tram = info?.isTram == true,
                            onClick = { nav.stop(s.entity, s.number, s.name) },
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.line_vehicle_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteStopRow(stop: ApiStop, first: Boolean, last: Boolean, arriving: Instant?, now: Instant, tram: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lineColor = cs.primary.copy(alpha = 0.8f)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = size.height / 2
            if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, y), strokeWidth = 4.dp.toPx())
            if (!last) drawLine(lineColor, Offset(x, y), Offset(x, size.height), strokeWidth = 4.dp.toPx())
            drawCircle(cs.surface, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(stop.name ?: stop.number, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            stop.municipality?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1) }
        }
        if (arriving != null) {
            val minutes = Math.floorDiv(arriving.toEpochMilli() - now.toEpochMilli(), 60_000L)
            Surface(shape = CircleShape, color = cs.primary.copy(alpha = 0.16f), contentColor = cs.primary) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (tram) Icons.Filled.Tram else Icons.Filled.DirectionsBus, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (minutes <= 0) stringResource(R.string.now) else stringResource(R.string.x_min_short, minutes.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
