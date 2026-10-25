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
import androidx.compose.material3.IconButton
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
            IconButton(onClick = ::findMe) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
        }
        androidx.compose.material3.BottomSheetScaffold(
            scaffoldState = sheet,
            sheetPeekHeight = peek + clearance,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            sheetContent = {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = clearance)) {
                    Text(
                        stringResource(R.string.stops_near_you),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
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
        } else {
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
