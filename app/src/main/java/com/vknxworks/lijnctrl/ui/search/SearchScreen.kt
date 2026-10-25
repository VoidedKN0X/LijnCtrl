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
import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.TabTitle
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Search tab: find any De Lijn stop by name (or by the number on its sign). */
@Composable
fun SearchScreen() {
    val c = LocalContext.current.container
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ApiStop>?>(null) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        // Waits until you stop typing for a moment, so every keystroke isn't a request.
        delay(350)
        loading = true
        runCatching { c.api.searchStops(q, max = 40) }
            .onSuccess { results = it.stops; total = it.total; error = null }
            .onFailure { error = it.friendlyMessage() }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_search))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            placeholder = { Text(stringResource(R.string.search_stops_hint)) },
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

        val list = results
        val err = error
        when {
            err != null && list == null -> MessageView(Icons.Outlined.SearchOff, stringResource(R.string.search_failed), err, Modifier.padding(bottom = LocalBottomClearance.current))
            list == null -> MessageView(
                Icons.Outlined.Signpost,
                stringResource(R.string.search_intro_title),
                stringResource(R.string.search_intro_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
            )
            list.isEmpty() -> MessageView(
                Icons.Outlined.SearchOff,
                stringResource(R.string.nothing_found),
                stringResource(R.string.try_fewer_or_different_words),
                Modifier.padding(bottom = LocalBottomClearance.current),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
            ) {
                items(list, key = { "${it.entity}_${it.number}" }) { stop -> StopResultRow(stop.entity, stop.number, stop.name ?: stop.number, stop.municipality, null) }
                if (total > list.size) {
                    item {
                        Text(
                            stringResource(R.string.showing_x_of_y, list.size, total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp),
                        )
                    }
                }
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
