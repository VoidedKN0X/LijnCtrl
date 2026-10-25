package com.vknxworks.lijnctrl.ui.stops

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiDisruption
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.localized
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.ui.components.DepartureRow
import com.vknxworks.lijnctrl.ui.components.DisruptionCard
import com.vknxworks.lijnctrl.ui.components.ErrorView
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.UpdatedLine
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** One stop: its departures (live where possible), the lines that call there, and today's disruptions. */
@Composable
fun StopScreen(entity: String, number: String, initialName: String?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val nav = LocalNavigator.current
    val key = remember(entity, number) { StopKey(entity, number) }
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val isSaved = saved.any { it.key == key }
    val now by rememberNow()

    var info by remember { mutableStateOf<ApiStop?>(null) }
    var servedLines by remember { mutableStateOf<List<LineKey>>(emptyList()) }
    var disruptions by remember { mutableStateOf<List<ApiDisruption>>(emptyList()) }
    var departures by remember { mutableStateOf<List<Departure>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updated by remember { mutableStateOf<Instant?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // Live departures topped up with the timetable, so quiet stops and lines without live data still show.
    suspend fun loadDepartures() {
        runCatching { c.departures.upcoming(key, max = 30) }
            .onSuccess {
                departures = it
                error = null
                updated = Instant.now()
            }
            .onFailure { error = it.friendlyMessage() }
    }

    // Details that don't change from minute to minute: once per visit.
    LaunchedEffect(key) {
        coroutineScope {
            val stop = async { runCatching { c.api.stop(key) }.getOrNull() }
            val lines = async { runCatching { c.api.stopLines(key) }.getOrNull() }
            val dis = async { runCatching { c.api.stopDisruptions(key) }.getOrDefault(emptyList()) }
            val div = async { runCatching { c.api.stopDiversions(key) }.getOrDefault(emptyList()) }
            info = stop.await()
            servedLines = lines.await().orEmpty().map { LineKey(it.entity, it.line) }.distinct()
            disruptions = (dis.await() + div.await() + disruptions).distinctBy { it.title to it.description }
        }
        info?.let { c.favourites.refreshName(key, it.name, it.municipality) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                loadDepartures()
                delay(REFRESH_MS)
            }
        }
    }

    val lineKeys = remember(departures, servedLines, disruptions) {
        (departures.orEmpty().mapNotNull { it.line } + servedLines + disruptions.flatMap { d -> d.lineDirections.map { LineKey(it.entity, it.line) } }).toSet()
    }
    val lines = rememberLineInfo(lineKeys)
    val name = info?.name ?: saved.firstOrNull { it.key == key }?.name ?: initialName ?: number
    val title = saved.firstOrNull { it.key == key }?.title ?: name

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(title) {
                IconButton(onClick = {
                    scope.launch { c.favourites.toggle(SavedStop(entity, number, name, info?.municipality)) }
                }) {
                    Icon(
                        if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        stringResource(if (isSaved) R.string.remove_from_stops else R.string.add_to_stops),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; loadDepartures(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    StopHeader(
                        name = if (title != name) name else null,
                        municipality = info?.municipality,
                        number = number,
                        accessible = info?.accessibility?.isNotEmpty() == true,
                    )
                }
                if (servedLines.isNotEmpty()) {
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            servedLines.sortedWith(compareBy<LineKey>({ lines[it.toString()]?.publicNumber?.length ?: 9 }, { lines[it.toString()]?.publicNumber ?: it.line }))
                                .forEach { k ->
                                    LineBadge(
                                        lines[k.toString()],
                                        k.line,
                                        minWidth = 36.dp,
                                        modifier = Modifier.clickable { nav.line(k.entity, k.line) },
                                    )
                                }
                        }
                    }
                }
                val localized = disruptions.map { it.localized(settings.messageLanguage.key) }
                if (localized.isNotEmpty()) {
                    items(localized) { d -> DisruptionCard(d, lines, Modifier.padding(horizontal = Gutter, vertical = 6.dp)) }
                }
                item { Spacer(Modifier.height(8.dp)) }
                val list = departures
                when {
                    list == null && error == null -> item { LoadingView(Modifier.height(240.dp)) }
                    list == null -> item { ErrorView(error.orEmpty(), Modifier.height(320.dp)) { scope.launch { loadDepartures() } } }
                    list.isEmpty() -> item {
                        MessageView(
                            Icons.Outlined.DepartureBoard,
                            stringResource(R.string.no_departures_soon),
                            stringResource(R.string.no_departures_soon_body),
                            Modifier.height(320.dp),
                            actionLabel = stringResource(R.string.full_timetable),
                            onAction = { nav.timetable(entity, number, title) },
                        )
                    }
                    else -> {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                            ) {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    list.forEachIndexed { i, d ->
                                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        DepartureRow(
                                            d,
                                            d.line?.let { lines[it.toString()] },
                                            now,
                                            settings.timeStyle,
                                            onClick = if (d.trip != null) ({ nav.trip(d.copy(stop = d.stop ?: number)) }) else null,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 10.dp)) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { nav.timetable(entity, number, title) },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                ) {
                                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.full_timetable))
                                }
                                UpdatedLine(updated)
                                if (error != null) Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Text(
                                    stringResource(R.string.live_legend),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopHeader(name: String?, municipality: String?, number: String, accessible: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)) {
        if (name != null) Text(name, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(municipality, context.getString(R.string.stop_number_x, number)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (accessible) {
                Spacer(Modifier.padding(4.dp))
                Icon(
                    Icons.Outlined.Accessible,
                    stringResource(R.string.accessible_stop),
                    Modifier.padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.copy_stop_number),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).clickable {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("stop", number))
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            },
        )
    }
}
