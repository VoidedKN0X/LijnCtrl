package com.vknxworks.lijnctrl.ui.stops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.departuresByStop
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.ui.components.CtrlDivider
import com.vknxworks.lijnctrl.ui.components.CtrlLabel
import com.vknxworks.lijnctrl.ui.components.DepartureRow
import com.vknxworks.lijnctrl.ui.components.NextDepartureHero
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.TabTitle
import com.vknxworks.lijnctrl.ui.components.UpdatedLine
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** How often departures refresh while they're on screen. */
const val REFRESH_MS = 30_000L

/** Departures shown per stop on the Stops tab. */
private const val PER_STOP = 3

/** The Stops tab: your saved stops, each with its next departures, live. */
@Composable
fun StopsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    val now by rememberNow()

    var departures by remember { mutableStateOf<Map<StopKey, List<Departure>>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<StopKey, String>>(emptyMap()) }
    var updated by remember { mutableStateOf<Instant?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var affecting by remember { mutableStateOf<Map<String, List<com.vknxworks.lijnctrl.data.api.Disruption>>>(emptyMap()) }

    suspend fun load() {
        val stops = saved.map { it.key }
        if (stops.isEmpty()) return
        val result = mutableMapOf<StopKey, List<Departure>>()
        val failed = mutableMapOf<StopKey, String>()
        // One call per group of stops; if De Lijn refuses a list, that group is asked stop by stop.
        coroutineScope {
            stops.chunked(10).map { group ->
                async {
                    val batch = runCatching { c.api.realtime(group, max = PER_STOP + 2) }.getOrNull()
                    if (batch != null) {
                        val byNumber = batch.flatMap { it.departuresByStop().entries }.associate { it.key to it.value }
                        group.map { k -> k to Result.success(byNumber[k.number].orEmpty()) }
                    } else {
                        group.map { k ->
                            k to runCatching { c.api.realtime(k, max = PER_STOP + 2).departuresByStop().let { m -> m[k.number] ?: m.values.flatten() } }
                        }
                    }
                }
            }.awaitAll().flatten()
        }.forEach { (k, r) ->
            r.onSuccess { result[k] = it }.onFailure { failed[k] = it.friendlyMessage() }
        }
        // Topped up with the timetable, so stops without live data (or quiet right now) aren't empty. Stops with a
        // line filter look further ahead, so there's still a handful left after filtering.
        val filters = c.favourites.filters.value
        coroutineScope {
            stops.map { k ->
                async {
                    val filter = filters[k.toString()].orEmpty()
                    k to runCatching {
                        if (filter.isEmpty()) c.departures.topUp(k, result[k].orEmpty(), PER_STOP)
                        else c.departures.topUp(k, result[k].orEmpty(), 30).filter { it.line?.toString() in filter }.take(PER_STOP)
                    }.getOrNull()
                }
            }.awaitAll()
        }.forEach { (k, merged) ->
            if (merged != null && (merged.isNotEmpty() || result.containsKey(k))) {
                result[k] = merged
                if (merged.isNotEmpty()) failed.remove(k)
            }
        }
        departures = result
        errors = failed
        updated = Instant.now()
        // Disruptions on the departures shown: the stop's own list, plus the line directions of what's shown
        // (cached for 10 minutes, so this doesn't add calls on every 30-second refresh).
        val language = c.settings.settings.value.messageLanguage.key
        affecting = coroutineScope {
            result.map { (k, list) ->
                async { runCatching { c.disruptions.forDepartures(k, list.take(PER_STOP), language, withLines = true) }.getOrDefault(emptyMap<String, List<com.vknxworks.lijnctrl.data.api.Disruption>>()) }
            }.awaitAll()
        }.fold(emptyMap<String, List<com.vknxworks.lijnctrl.data.api.Disruption>>()) { acc, m -> acc + m }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lineFilters by c.favourites.filters.collectAsStateWithLifecycle()
    LaunchedEffect(saved.map { it.key }, lineFilters) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                load()
                delay(REFRESH_MS)
            }
        }
    }

    val lineKeys = remember(departures) { departures.values.flatten().mapNotNull { it.line }.toSet() }
    val lines = rememberLineInfo(lineKeys)

    val savedLines by c.favourites.lines.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_stops))
        if (savedLines.isNotEmpty()) SavedLinesRow(savedLines)
        if (saved.isEmpty()) {
            MessageView(
                Icons.Outlined.StarBorder,
                stringResource(R.string.no_saved_stops),
                stringResource(R.string.no_saved_stops_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
            )
            return@Column
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                items(saved, key = { it.key.toString() }) { stop ->
                    StopCard(
                        stop = stop,
                        departures = departures[stop.key]?.take(PER_STOP),
                        error = errors[stop.key],
                        lines = lines,
                        affecting = affecting,
                        now = now,
                        index = saved.indexOf(stop),
                        count = saved.size,
                        onOpen = { nav.stop(stop.entity, stop.number, stop.title) },
                    )
                }
                item { UpdatedLine(updated, Modifier.fillMaxWidth().padding(4.dp)) }
            }
        }
    }
}

@Composable
private fun StopCard(
    stop: SavedStop,
    departures: List<Departure>?,
    error: String?,
    lines: Map<String, com.vknxworks.lijnctrl.data.lines.LineInfo>,
    affecting: Map<String, List<com.vknxworks.lijnctrl.data.api.Disruption>>,
    now: Instant,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The stop: its number (mono label) and name, tap to open it; the menu sits on the right.
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CtrlLabel(
                    listOfNotNull(
                        stringResource(R.string.stop_number_x, stop.number),
                        stop.name.takeIf { stop.nickname != null },
                        stop.municipality,
                    ).joinToString(" · "),
                )
                Text(
                    stop.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                    containerColor = com.vknxworks.lijnctrl.ui.theme.CtrlColors.Raised,
                    border = com.vknxworks.lijnctrl.ui.theme.ControlBorder,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    if (index > 0) DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                        onClick = { menu = false; scope.launch { c.favourites.move(stop.key, -1) } },
                    )
                    if (index < count - 1) DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                        onClick = { menu = false; scope.launch { c.favourites.move(stop.key, 1) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_stops), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; scope.launch { c.favourites.remove(stop.key) } },
                    )
                }
            }
        }
        when {
            departures == null && error == null -> Text(
                stringResource(R.string.loading_departures),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error != null && departures == null -> Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            departures.isNullOrEmpty() -> Text(
                stringResource(R.string.no_departures_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val list = departures.orEmpty()
                val first = list.first()
                // The next one is the hero: in the accent colour for your first stop, on a card for the others.
                NextDepartureHero(
                    first,
                    first.line?.let { lines[it.toString()] },
                    now,
                    settings.timeStyle,
                    accent = index == 0,
                    disruptions = affecting[first.matchKey].orEmpty(),
                    onClick = if (first.trip != null) ({ nav.trip(first.copy(stop = first.stop ?: stop.number)) }) else null,
                )
                if (list.size > 1) {
                    Column {
                        list.drop(1).forEach { d ->
                            CtrlDivider()
                            DepartureRow(
                                d,
                                d.line?.let { lines[it.toString()] },
                                now,
                                settings.timeStyle,
                                compact = true,
                                disruptions = affecting[d.matchKey].orEmpty(),
                                onClick = if (d.trip != null) ({ nav.trip(d.copy(stop = d.stop ?: stop.number)) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(stop.nickname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            modifier = com.vknxworks.lijnctrl.ui.theme.CtrlDialogModifier,
            title = { Text(stringResource(R.string.rename_stop)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rename_stop_body, stop.name), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        text,
                        { text = it.take(40) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.name)) },
                        shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                        colors = com.vknxworks.lijnctrl.ui.components.ctrlFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { renaming = false; scope.launch { c.favourites.rename(stop.key, text) } }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Your saved lines, as badges that open the line's page. */
@Composable
private fun SavedLinesRow(saved: List<com.vknxworks.lijnctrl.data.favourites.SavedLine>) {
    val nav = LocalNavigator.current
    val info = rememberLineInfo(saved.map { it.key })
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        items(saved, key = { it.key.toString() }) { l ->
            com.vknxworks.lijnctrl.ui.components.LineBadge(
                info[l.key.toString()],
                l.publicNumber,
                large = true,
                modifier = Modifier.clickable { nav.line(l.entity, l.line) },
            )
        }
    }
}
