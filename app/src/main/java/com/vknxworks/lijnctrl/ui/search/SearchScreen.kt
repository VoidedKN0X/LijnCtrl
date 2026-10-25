package com.vknxworks.lijnctrl.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiLocation
import com.vknxworks.lijnctrl.data.api.ApiSearchLineDirection
import com.vknxworks.lijnctrl.data.api.StopSearchResponse
import com.vknxworks.lijnctrl.data.towns.TownRepository
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.towns.Town
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.lines.directionEnd
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material3.TextButton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.TabTitle
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Results for one query: each part loads on its own, so one failing doesn't hide the rest. */
private data class Results(
    val numberMatch: ApiStop? = null,
    val towns: List<Town> = emptyList(),
    val lines: List<List<ApiSearchLineDirection>> = emptyList(),
    val stops: List<ApiStop> = emptyList(),
    val totalStops: Int = 0,
    val places: List<ApiLocation> = emptyList(),
    val stopsError: String? = null,
)

/** The Search tab: stops by name or the number on their sign, lines by number, and every stop in a town. */
@Composable
fun SearchScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Results?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty() || (q.length < 2 && !q.all(Char::isDigit))) {
            results = null
            loading = false
            return@LaunchedEffect
        }
        // Waits until you stop typing for a moment, so every keystroke isn't a request.
        delay(350)
        loading = true
        results = coroutineScope {
            val number = async { if (q.all(Char::isDigit) && q.length in 5..6) findByNumber(c.api, q) else null }
            val towns = async { if (q.any(Char::isLetter)) c.towns.matching(q, max = 4) else emptyList() }
            val lines = async {
                runCatching { c.api.searchLines(q, max = 30).directions }.getOrDefault(emptyList()).also { found ->
                    runCatching { c.lines.remember(found) }
                }
            }
            val stops = async { runCatching { searchStopsAnyOrder(c.api, q) } }
            val places = async { runCatching { c.api.searchLocations(q, max = 8).locations }.getOrDefault(emptyList()) }
            val st = stops.await()
            Results(
                numberMatch = number.await(),
                towns = towns.await(),
                lines = lines.await().groupBy { LineKey(it.entity, it.line) }.values.toList().take(8),
                stops = st.getOrNull()?.stops.orEmpty(),
                totalStops = st.getOrNull()?.total ?: 0,
                stopsError = st.exceptionOrNull()?.friendlyMessage(),
                // Stops already listed above aren't repeated as places; places without a position can't be used.
                places = places.await().filter { it.coordinate != null && it.coordinate.latitude != 0.0 && it.stopNumber.isNullOrBlank() }.take(6),
            )
        }
        loading = false
    }

    fun loadMore() {
        val r = results ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { c.api.searchStops(query.trim(), max = PAGE, start = r.stops.size) }.onSuccess { more ->
                results = r.copy(stops = (r.stops + more.stops).distinctBy { it.entity to it.number }, totalStops = more.total)
            }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_search))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
        )
        Spacer(Modifier.height(8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Gutter)) else Spacer(Modifier.height(4.dp))

        val r = results
        val bottom = Modifier.padding(bottom = LocalBottomClearance.current)
        when {
            r == null -> MessageView(Icons.Outlined.Signpost, stringResource(R.string.search_intro_title), stringResource(R.string.search_intro_body), bottom)
            r.numberMatch == null && r.towns.isEmpty() && r.lines.isEmpty() && r.stops.isEmpty() && r.places.isEmpty() -> {
                if (r.stopsError != null) MessageView(Icons.Outlined.SearchOff, stringResource(R.string.search_failed), r.stopsError, bottom)
                else MessageView(Icons.Outlined.SearchOff, stringResource(R.string.nothing_found), stringResource(R.string.try_fewer_or_different_words), bottom)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
            ) {
                r.numberMatch?.let { s ->
                    item { SectionTitle(stringResource(R.string.stop_number_match)) }
                    item(key = "number") { StopResultRow(s.entity, s.number, s.name ?: s.number, s.municipality, null) }
                }
                if (r.places.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.places)) }
                    items(r.places, key = { "place-${it.coordinate?.latitude}_${it.coordinate?.longitude}_${it.name}_${it.street}_${it.houseNumber}" }) { p ->
                        PlaceRow(p) {
                            val coord = p.coordinate ?: return@PlaceRow
                            nav.place(coord.latitude, coord.longitude, placeTitle(p), placeDetail(p))
                        }
                    }
                }
                if (r.towns.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.towns)) }
                    items(r.towns, key = { "town-${it.number}" }) { t -> TownRow(t) { nav.town(t.number, t.name) } }
                }
                if (r.lines.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.lines)) }
                    items(r.lines, key = { "line-${it.first().entity}_${it.first().line}" }) { dirs ->
                        LineResultRow(dirs) { nav.line(dirs.first().entity, dirs.first().line) }
                    }
                }
                if (r.stops.isNotEmpty() || r.stopsError != null) item { SectionTitle(stringResource(R.string.tab_stops)) }
                if (r.stopsError != null && r.stops.isEmpty()) {
                    item {
                        Text(r.stopsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp))
                    }
                }
                items(r.stops, key = { "${it.entity}_${it.number}" }) { stop -> StopResultRow(stop.entity, stop.number, stop.name ?: stop.number, stop.municipality, null) }
                if (r.totalStops > r.stops.size) {
                    item {
                        TextButton(
                            onClick = ::loadMore,
                            enabled = !loadingMore,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
                        ) { Text(if (loadingMore) stringResource(R.string.loading) else stringResource(R.string.show_more_x_of_y, r.stops.size, r.totalStops)) }
                    }
                }
            }
        }
    }
}

private const val PAGE = 40

/**
 * Stop search that doesn't care about word order. De Lijn's search matches the words as typed, so "korenmarkt gent"
 * found nothing while "gent korenmarkt" did. When the query as typed finds nothing, each word is searched on its
 * own and the stops that contain every word (in their name or town) are kept.
 */
internal suspend fun searchStopsAnyOrder(api: DeLijnApi, q: String): StopSearchResponse {
    val direct = api.searchStops(q, max = PAGE)
    val words = q.split(Regex("\\s+")).map { TownRepository.fold(it) }.filter { it.length >= 3 }.distinct()
    if (direct.stops.isNotEmpty() || words.size < 2) return direct
    val found = coroutineScope {
        words.take(4).map { w -> async { runCatching { api.searchStops(w, max = 100).stops }.getOrDefault(emptyList()) } }.flatMap { it.await() }
    }.distinctBy { it.entity to it.number }
    fun text(s: ApiStop) = TownRepository.fold(listOfNotNull(s.name, s.longName, s.municipality).joinToString(" "))
    val matching = found.filter { s -> words.all { w -> text(s).contains(w) } }
    return StopSearchResponse(total = matching.size, stops = matching)
}

/** What a place is called: its name, or its street and number. */
fun placeTitle(p: ApiLocation): String =
    p.name?.takeIf { it.isNotBlank() } ?: p.altName?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(p.street, p.houseNumber).joinToString(" ").ifBlank { p.town.orEmpty() }

/** Where it is: street (when the name isn't the street) and town. */
fun placeDetail(p: ApiLocation): String? {
    val street = listOfNotNull(p.street, p.houseNumber).joinToString(" ").takeIf { it.isNotBlank() && it != placeTitle(p) }
    val town = (p.districts.firstOrNull() ?: p.town)?.let { TownRepository.titleCase(it) }
    return listOfNotNull(street, town).joinToString(", ").ifBlank { null }
}

@Composable
private fun PlaceRow(p: ApiLocation, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (p.houseNumber.isNullOrBlank() && !p.name.isNullOrBlank()) Icons.Outlined.Place else Icons.Outlined.Home,
            null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(placeTitle(p), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(placeDetail(p), stringResource(R.string.stops_nearby_short)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The number on a stop sign: tried in its own region first (the first digit), then in the others. */
private suspend fun findByNumber(api: DeLijnApi, number: String): ApiStop? {
    val first = number.take(1)
    val entities = (listOf(first) + listOf("1", "2", "3", "4", "5")).distinct()
    for (e in entities) {
        val s = runCatching { api.stop(StopKey(e, number)) }.getOrNull()
        if (s != null) return s
    }
    return null
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun TownRow(t: Town, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationCity, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (t.partOf != null) stringResource(R.string.all_stops_in_x_part_of_y, t.name, t.partOf) else stringResource(R.string.all_stops_in_x, t.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A line with its directions ("to Moscou · to Flanders Expo"). */
@Composable
private fun LineResultRow(dirs: List<ApiSearchLineDirection>, onClick: () -> Unit) {
    val first = dirs.first()
    val info = rememberLineInfo(listOf(LineKey(first.entity, first.line)))[LineKey(first.entity, first.line).toString()]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(info, first.publicNumber ?: first.line, minWidth = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val ends = dirs.mapNotNull { directionEnd(it.description) }.distinct()
            val context = LocalContext.current
            Text(
                if (ends.isNotEmpty()) ends.joinToString("  ·  ") { context.getString(R.string.to_x, it) } else first.description.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            first.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A stop in a list: name, where it is, and a star to save it. Shared with the Nearby tab. */
@Composable
fun StopResultRow(entity: String, number: String, name: String, municipality: String?, distance: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    val isSaved = saved.any { it.entity == entity && it.number == number }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.stop(entity, number, name) }
            .padding(start = Gutter, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(distance, municipality, stringResource(R.string.stop_number_x, number)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { scope.launch { c.favourites.toggle(SavedStop(entity, number, name, municipality)) } }) {
            Icon(
                if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                stringResource(if (isSaved) R.string.remove_from_stops else R.string.add_to_stops),
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
