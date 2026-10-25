package com.vknxworks.lijnctrl.ui.plan

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiLocation
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.plan.PlanPoint
import com.vknxworks.lijnctrl.str
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.search.placeDetail
import com.vknxworks.lijnctrl.ui.search.placeTitle
import com.vknxworks.lijnctrl.ui.search.searchStopsAnyOrder
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Picks the start or end of a route: where you are, a saved stop, or any stop, address or place. */
@Composable
fun PickPlaceScreen(forFrom: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var stops by remember { mutableStateOf<List<ApiStop>>(emptyList()) }
    var places by remember { mutableStateOf<List<ApiLocation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun choose(p: PlanPoint) {
        if (forFrom) PlanState.from = p else PlanState.to = p
        PlanState.results = null
        PlanState.error = null
        nav.back()
    }

    /** Saved stops don't keep their position, so it's looked up when you pick one. */
    fun chooseStop(key: StopKey, name: String, town: String?) {
        scope.launch {
            runCatching { c.api.stop(key) }
                .onSuccess { s ->
                    val coord = s.coordinate
                    if (coord == null || coord.latitude == 0.0) Toast.makeText(context, str(R.string.no_position_for_stop), Toast.LENGTH_SHORT).show()
                    else choose(PlanPoint(name, coord.latitude, coord.longitude, stop = key, detail = s.municipality ?: town))
                }
                .onFailure { Toast.makeText(context, it.friendlyMessage(), Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            stops = emptyList()
            places = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        coroutineScope {
            val st = async { runCatching { searchStopsAnyOrder(c.api, q).stops }.getOrDefault(emptyList()) }
            val pl = async { runCatching { c.api.searchLocations(q, max = 8).locations }.getOrDefault(emptyList()) }
            stops = st.await().take(25)
            places = pl.await().filter { it.coordinate != null && it.coordinate.latitude != 0.0 && it.stopNumber.isNullOrBlank() }.take(8)
        }
        loading = false
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(if (forFrom) R.string.from else R.string.to)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text(stringResource(R.string.pick_place_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).focusRequester(focus),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Gutter))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (query.trim().length < 2) {
                    item {
                        PickRow(Icons.Outlined.MyLocation, stringResource(R.string.my_location), stringResource(R.string.my_location_detail)) {
                            choose(PlanPoint(str(R.string.my_location), 0.0, 0.0, isMyLocation = true))
                        }
                    }
                    items(saved, key = { "saved-${it.key}" }) { st ->
                        PickRow(Icons.Outlined.Star, st.title, listOfNotNull(st.name.takeIf { st.nickname != null }, st.municipality).joinToString(" · ").ifBlank { null }) {
                            chooseStop(st.key, st.title, st.municipality)
                        }
                    }
                } else {
                    items(places, key = { "place-${it.coordinate?.latitude}_${it.coordinate?.longitude}_${it.name}_${it.street}_${it.houseNumber}" }) { p ->
                        PickRow(if (p.houseNumber.isNullOrBlank() && !p.name.isNullOrBlank()) Icons.Outlined.Place else Icons.Outlined.Home, placeTitle(p), placeDetail(p)) {
                            val coord = p.coordinate ?: return@PickRow
                            choose(PlanPoint(placeTitle(p), coord.latitude, coord.longitude, detail = placeDetail(p)))
                        }
                    }
                    items(stops, key = { "stop-${it.entity}_${it.number}" }) { st ->
                        val name = st.name ?: st.number
                        PickRow(Icons.Outlined.DirectionsBus, name, listOfNotNull(st.municipality, stringResource(R.string.stop_number_x, st.number)).joinToString(" · ")) {
                            val coord = st.coordinate
                            if (coord != null && coord.latitude != 0.0) choose(PlanPoint(name, coord.latitude, coord.longitude, stop = StopKey(st.entity, st.number), detail = st.municipality))
                            else chooseStop(StopKey(st.entity, st.number), name, st.municipality)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
