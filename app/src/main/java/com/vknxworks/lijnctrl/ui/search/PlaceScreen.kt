package com.vknxworks.lijnctrl.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.ui.components.LoadContent
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.rememberLoader
import com.vknxworks.lijnctrl.ui.theme.Gutter

/**
 * A place from the search (an address, a shop, a landmark) and the De Lijn stops around it, closest first.
 * This is how a search like "Vandenborre Roeselare" gets you to the right stop.
 */
@Composable
fun PlaceScreen(latitude: Double, longitude: Double, name: String, detail: String?) {
    val c = LocalContext.current.container
    // Starts close by and widens the circle until there's a handful of stops, up to 2 km.
    val loader = rememberLoader("place-$latitude,$longitude") {
        var found = emptyList<com.vknxworks.lijnctrl.data.api.ApiNearbyStop>()
        for (radius in listOf(500, 1000, 2000)) {
            found = c.api.stopsNearby(latitude, longitude, max = 30, radius = radius).stops
                .filter { it.type == null || it.type.equals("DELIJN", true) }
                .sortedBy { it.distance ?: Int.MAX_VALUE }
            if (found.size >= 4) break
        }
        found
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(name) {
                val nav = com.vknxworks.lijnctrl.ui.nav.LocalNavigator.current
                androidx.compose.material3.IconButton(onClick = {
                    nav.routeTo(com.vknxworks.lijnctrl.data.plan.PlanPoint(name, latitude, longitude, detail = detail))
                }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Directions, stringResource(R.string.route_here)) }
            }
        },
    ) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { stops ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    )
                }
                if (stops.isEmpty()) {
                    MessageView(Icons.Outlined.LocationOff, stringResource(R.string.no_stops_near_place), stringResource(R.string.no_stops_near_place_body))
                } else {
                    Text(
                        stringResource(R.string.stops_near_place),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 4.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(stops, key = { it.id }) { stop ->
                            StopResultRow(
                                entity = DeLijnApi.entityFromLinks(stop) ?: stop.id.take(1),
                                number = stop.id,
                                name = stop.name ?: stop.id,
                                municipality = null,
                                distance = stop.distance?.let { stringResource(R.string.x_m_away, it) },
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.place_footer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
