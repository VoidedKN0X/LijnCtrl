package com.vknxworks.lijnctrl.ui.stops

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.vknxworks.lijnctrl.data.plan.LegMode
import com.vknxworks.lijnctrl.data.plan.OtherDeparture
import com.vknxworks.lijnctrl.ui.components.CtrlDivider
import com.vknxworks.lijnctrl.ui.components.DepartureRow
import com.vknxworks.lijnctrl.ui.components.ErrorView
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.UpdatedLine
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.components.rememberNow
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Departures at a stop or station of another Belgian operator (NMBS/SNCB, STIB/MIVB, TEC…), from Transitous's open
 * API: no key needed, and only reachable with "Use Transitous" on. Live where the operator publishes live data.
 */
@Composable
fun OtherStopScreen(stopId: String, name: String) {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var title by remember { mutableStateOf(name) }
    var list by remember { mutableStateOf<List<OtherDeparture>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updated by remember { mutableStateOf<Instant?>(null) }

    suspend fun load() {
        runCatching { c.transitous.stopTimes(stopId) }
            .onSuccess { (n, l) ->
                n?.let { title = it }
                list = l
                error = null
                updated = Instant.now()
            }
            .onFailure { error = it.friendlyMessage() }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(stopId, settings.useTransitous) {
        if (!settings.useTransitous) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                load()
                delay(REFRESH_MS)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title) },
    ) { padding ->
        val l = list
        when {
            !settings.useTransitous -> MessageView(
                Icons.Outlined.Directions,
                stringResource(R.string.other_stop_needs_transitous),
                stringResource(R.string.other_stop_needs_transitous_body),
                Modifier.padding(padding),
                actionLabel = stringResource(R.string.turn_on),
                onAction = { scope.launch { c.settings.update { it.copy(useTransitous = true) } } },
            )
            l == null && error == null -> LoadingView(Modifier.padding(padding))
            l == null -> ErrorView(error.orEmpty(), Modifier.padding(padding)) { scope.launch { load() } }
            l.isEmpty() -> MessageView(
                Icons.Outlined.DepartureBoard,
                stringResource(R.string.no_departures_soon),
                stringResource(R.string.other_stop_none_body),
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(l, key = { it.departure.id }) { o ->
                    Column(Modifier.padding(horizontal = Gutter)) {
                        CtrlDivider()
                        DepartureRow(o.departure, o.badge, now, settings.timeStyle)
                        val details = listOfNotNull(
                            o.operator,
                            modeLabel(o.mode),
                            o.track?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.platform_x, it) },
                        ).joinToString(" · ")
                        if (details.isNotBlank()) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 12.dp)) {
                        UpdatedLine(updated)
                        if (error != null) Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.other_stop_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: LegMode): String? = when (mode) {
    LegMode.TRAIN -> stringResource(R.string.mode_train)
    LegMode.METRO -> stringResource(R.string.mode_metro)
    LegMode.TRAM -> stringResource(R.string.mode_tram)
    LegMode.BUS -> stringResource(R.string.mode_bus)
    else -> null
}

private const val REFRESH_MS = 30_000L
