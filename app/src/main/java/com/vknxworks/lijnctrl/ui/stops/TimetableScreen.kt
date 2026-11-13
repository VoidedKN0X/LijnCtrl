package com.vknxworks.lijnctrl.ui.stops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.settings.TimeStyle
import com.vknxworks.lijnctrl.ui.components.DepartureRow
import com.vknxworks.lijnctrl.ui.components.ErrorView
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A stop's full timetable for one day, from first to last bus, with a filter per line. */
@Composable
fun TimetableScreen(entity: String, number: String, name: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val key = remember(entity, number) { StopKey(entity, number) }
    val today = remember { LocalDate.now(BRUSSELS) }
    val days = remember(today) { (0L..6L).map { today.plusDays(it) } }
    var dayIndex by rememberSaveable { mutableStateOf(0) }
    var lineFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<Departure>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val now by rememberNow(30_000L)
    val day = days[dayIndex.coerceIn(days.indices)]

    LaunchedEffect(day, attempt) {
        list = null
        error = null
        runCatching { c.departures.timetable(key, day) }
            .onSuccess { list = it.sortedBy { d -> d.scheduled } }
            .onFailure { error = it.friendlyMessage() }
    }

    val all = list.orEmpty()
    val lineKeys = remember(all) { all.mapNotNull { it.line }.toSet() }
    val lines = rememberLineInfo(lineKeys)
    val shown = remember(all, lineFilter) { if (lineFilter == null) all else all.filter { it.line?.toString() == lineFilter } }
    val byHour = remember(shown) { shown.groupBy { it.scheduled.atZone(BRUSSELS).hour } }
    val listState = rememberLazyListState()

    // Today: start at the current hour rather than at five in the morning.
    LaunchedEffect(byHour, dayIndex) {
        if (dayIndex == 0 && byHour.isNotEmpty()) {
            val hourNow = Instant.now().atZone(BRUSSELS).hour
            val hours = byHour.keys.sorted()
            val target = hours.indexOfFirst { it >= hourNow }.takeIf { it >= 0 } ?: return@LaunchedEffect
            // Header items before the hours: days row and the lines row.
            val before = 2 + hours.take(target).sumOf { (byHour[it]?.size ?: 0) + 1 }
            listState.scrollToItem(before)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.timetable_at_x, name ?: number)) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    items(days.size) { i ->
                        com.vknxworks.lijnctrl.ui.components.CtrlChip(
                            selected = i == dayIndex,
                            onClick = { dayIndex = i },
                            label = { Text(dayLabel(days[i], today)) },
                        )
                    }
                }
            }
            item {
                if (lineKeys.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        item {
                            com.vknxworks.lijnctrl.ui.components.CtrlChip(selected = lineFilter == null, onClick = { lineFilter = null }, label = { Text(stringResource(R.string.all_lines)) })
                        }
                        items(lineKeys.sortedBy { lines[it.toString()]?.publicNumber?.padStart(4, '0') ?: it.line }.toList()) { k ->
                            com.vknxworks.lijnctrl.ui.components.CtrlChip(
                                selected = lineFilter == k.toString(),
                                onClick = { lineFilter = if (lineFilter == k.toString()) null else k.toString() },
                                label = { LineBadge(lines[k.toString()], k.line, minWidth = 28.dp) },
                            )
                        }
                    }
                }
            }
            val err = error
            when {
                err != null -> item { ErrorView(err, Modifier.height(320.dp)) { attempt++ } }
                list == null -> item { LoadingView(Modifier.height(320.dp)) }
                shown.isEmpty() -> item {
                    MessageView(Icons.Outlined.EventBusy, stringResource(R.string.no_departures_this_day), stringResource(R.string.no_departures_this_day_body), Modifier.height(320.dp))
                }
                else -> byHour.keys.sorted().forEach { hour ->
                    item(key = "h$hour") {
                        Text(
                            "%02d:00".format(hour),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(byHour[hour].orEmpty(), key = { it.id }) { d ->
                        val gone = d.scheduled.isBefore(now)
                        DepartureRow(
                            d,
                            d.line?.let { lines[it.toString()] },
                            now,
                            TimeStyle.CLOCK,
                            compact = true,
                            modifier = if (gone) Modifier.alpha(0.5f) else Modifier,
                            onClick = if (d.trip != null) ({ nav.trip(d.copy(stop = d.stop ?: number)) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + DateTimeFormatter.ofPattern("d MMM").format(day)
}
