package com.vknxworks.lijnctrl.ui.nearby

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.util.GeoPoint
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.api.departuresByStop
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiNearbyStop
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.location.Locator
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.LoadingView
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.TabTitle
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.search.StopResultRow
import com.vknxworks.lijnctrl.ui.theme.Gutter

private sealed interface NearbyState {
    data object Idle : NearbyState
    data object Locating : NearbyState
    data object NoFix : NearbyState
    data object LocationOff : NearbyState
    data class Failed(val message: String) : NearbyState
    data class Found(val stops: List<ApiNearbyStop>) : NearbyState
}

/**
 * The Nearby tab: stops within walking distance. Your location is read once, only when you ask, from Android's
 * own location service, and is only sent to De Lijn's API to look up the stops around it.
 */
@Composable
fun NearbyScreen() {
    val context = LocalContext.current
    val c = context.container
    var state by remember { mutableStateOf<NearbyState>(NearbyState.Idle) }
    var trigger by remember { mutableIntStateOf(0) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var here by remember { mutableStateOf<GeoPoint?>(null) }

    suspend fun locate() {
        if (Locator.isOff(context)) {
            state = NearbyState.LocationOff
            return
        }
        if (state !is NearbyState.Found) state = NearbyState.Locating
        val loc = Locator.current(context)
        if (loc == null) {
            state = NearbyState.NoFix
            return
        }
        MapMemory.center = GeoPoint(loc.latitude, loc.longitude)
        here = GeoPoint(loc.latitude, loc.longitude)
        // De Lijn caps the list at 50 stops; the distance is your choice in Settings (1 km by default).
        val radius = c.settings.settings.value.nearbyRadius.metres
        val found = runCatching { c.api.stopsNearby(loc.latitude, loc.longitude, max = 50, radius = radius) }
        state = found.getOrNull()?.let { r ->
            // Only De Lijn's own stops: others (train stations and the like) can't show De Lijn departures.
            NearbyState.Found(r.stops.filter { it.type == null || it.type.equals("DELIJN", true) }.sortedBy { it.distance ?: Int.MAX_VALUE })
        } ?: NearbyState.Failed(found.exceptionOrNull()?.friendlyMessage().orEmpty())
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) trigger++
    }

    fun findMe() {
        if (Locator.hasPermission(context)) {
            trigger++
        } else {
            AppLock.expectReturn()
            permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    // Looks straight away when location is already allowed; otherwise only after you tap the button.
    LaunchedEffect(Unit) {
        if (Locator.hasPermission(context) && state is NearbyState.Idle) trigger++
    }
    LaunchedEffect(trigger) { if (trigger > 0) locate() }

    // One screen: the map fills it, and the stops near you are in a sheet you pull up from the bottom.
    val clearance = LocalBottomClearance.current
    val peek = 168.dp
    val sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_nearby)) {
            com.vknxworks.lijnctrl.ui.components.CtrlSquareButton(Icons.Outlined.Refresh, stringResource(R.string.refresh), onClick = ::findMe)
        }
        androidx.compose.material3.BottomSheetScaffold(
            scaffoldState = sheet,
            sheetPeekHeight = peek + clearance,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
            sheetContent = {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = clearance)) {
                    com.vknxworks.lijnctrl.ui.components.CtrlLabel(
                        stringResource(R.string.stops_near_you),
                        Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                    )
                    NearbyList(state, settings.nearbyRadius.metres, ::findMe, { trigger++ }, context)
                }
            },
        ) { inner ->
            // The map's own buttons and credit sit just above the sheet.
            androidx.compose.runtime.CompositionLocalProvider(LocalBottomClearance provides peek + clearance) {
                StopMap(start = MapMemory.center, modifier = Modifier.padding(top = inner.calculateTopPadding()), focus = here)
            }
        }
    }
}

/** What's in the Nearby sheet: the stops around you, closest first, or why there aren't any. */
@Composable
private fun NearbyList(state: NearbyState, radius: Int, findMe: () -> Unit, retry: () -> Unit, context: android.content.Context) {
    when (state) {
        NearbyState.Idle -> MessageView(
            Icons.Outlined.MyLocation,
            stringResource(R.string.nearby_intro_title),
            stringResource(R.string.nearby_intro_body),
            Modifier.height(260.dp),
            actionLabel = stringResource(R.string.find_stops_near_me),
            onAction = findMe,
        )
        NearbyState.Locating -> Column {
            Text(
                stringResource(R.string.finding_where_you_are),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter),
            )
            LoadingView(Modifier.height(160.dp))
        }
        NearbyState.LocationOff -> MessageView(
            Icons.Outlined.LocationOff,
            stringResource(R.string.location_is_off),
            stringResource(R.string.location_is_off_body),
            Modifier.height(260.dp),
            actionLabel = stringResource(R.string.open_location_settings),
            onAction = {
                runCatching {
                    AppLock.expectReturn()
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            },
        )
        NearbyState.NoFix -> MessageView(
            Icons.Outlined.WrongLocation,
            stringResource(R.string.no_location_fix),
            stringResource(R.string.no_location_fix_body),
            Modifier.height(260.dp),
            actionLabel = stringResource(R.string.try_again),
            onAction = retry,
        )
        is NearbyState.Failed -> MessageView(
            Icons.Outlined.WrongLocation,
            stringResource(R.string.couldn_t_load_this),
            state.message,
            Modifier.height(260.dp),
            actionLabel = stringResource(R.string.try_again),
            onAction = retry,
        )
        is NearbyState.Found -> if (state.stops.isEmpty()) {
            MessageView(Icons.Outlined.LocationOff, stringResource(R.string.no_stops_nearby), stringResource(R.string.no_stops_nearby_body_x, radius), Modifier.height(260.dp))
        } else Column {
            // Two views of the same stops: the stops themselves, or every departure from them, soonest first.
            var board by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                com.vknxworks.lijnctrl.ui.components.CtrlChip(selected = !board, onClick = { board = false }, label = { Text(stringResource(R.string.stops)) })
                com.vknxworks.lijnctrl.ui.components.CtrlChip(selected = board, onClick = { board = true }, label = { Text(stringResource(R.string.departures)) })
            }
            if (board) {
                DeparturesBoard(state.stops)
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.stops, key = { it.id }) { stop ->
                    val entity = DeLijnApi.entityFromLinks(stop) ?: stop.id.take(1)
                    StopResultRow(
                        entity = entity,
                        number = stop.id,
                        name = stop.name ?: stop.id,
                        municipality = null,
                        distance = stop.distance?.let { stringResource(R.string.x_m_away, it) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.nearby_footer_x, radius),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** How many of the closest stops the departures board looks at. */
private const val BOARD_STOPS = 8

/**
 * Every departure from the stops around you in one list, soonest first, with the stop each one leaves from and
 * how far away it is. Live where De Lijn has live times, topped up with the timetable; refreshed every 30 seconds.
 */
@Composable
private fun DeparturesBoard(stops: List<ApiNearbyStop>) {
    val c = LocalContext.current.container
    val nav = com.vknxworks.lijnctrl.ui.nav.LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val now by com.vknxworks.lijnctrl.ui.components.rememberNow()
    val closest = remember(stops) { stops.take(BOARD_STOPS) }
    val keys = remember(closest) {
        closest.map { com.vknxworks.lijnctrl.data.api.StopKey(DeLijnApi.entityFromLinks(it) ?: it.id.take(1), it.id) }
    }
    var rows by remember { mutableStateOf<List<com.vknxworks.lijnctrl.data.api.Departure>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(keys) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                val live = runCatching { c.api.realtime(keys, max = 5) }
                    .map { list -> list.flatMap { it.departuresByStop().entries }.associate { it.key to it.value } }
                val merged = kotlinx.coroutines.coroutineScope {
                    keys.map { k ->
                        async {
                            val l = live.getOrNull()?.get(k.number).orEmpty()
                            runCatching { c.departures.topUp(k, l, 5) }.getOrDefault(l).map { it.copy(stop = it.stop ?: k.number) }
                        }
                    }.awaitAll().flatten()
                }
                val nowI = java.time.Instant.now()
                rows = merged.filter { !it.time.isBefore(nowI.minusSeconds(30)) }.distinctBy { it.matchKey }.sortedBy { it.time }.take(40)
                failed = if (merged.isEmpty()) live.exceptionOrNull()?.friendlyMessage() else null
                delay(30_000)
            }
        }
    }

    val list = rows
    val lines = com.vknxworks.lijnctrl.ui.components.rememberLineInfo(list.orEmpty().mapNotNull { it.line }.toSet())
    val byNumber = remember(closest) { closest.associateBy { it.id } }
    when {
        list == null && failed == null -> LoadingView(Modifier.height(160.dp))
        list.isNullOrEmpty() -> Text(
            failed ?: stringResource(R.string.no_departures_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Gutter, vertical = 16.dp),
        )
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.matchKey }) { d ->
                val stop = d.stop?.let { byNumber[it] }
                Column {
                    com.vknxworks.lijnctrl.ui.components.DepartureRow(
                        d,
                        d.line?.let { lines[it.toString()] },
                        now,
                        settings.timeStyle,
                        compact = true,
                        onClick = if (d.trip != null) ({ nav.trip(d) }) else null,
                    )
                    if (stop != null) {
                        Text(
                            listOfNotNull(stop.name, stop.distance?.let { stringResource(R.string.x_m_away, it) }).joinToString(" · "),
                            style = com.vknxworks.lijnctrl.ui.theme.CtrlType.DataSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp + 54.dp, bottom = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
