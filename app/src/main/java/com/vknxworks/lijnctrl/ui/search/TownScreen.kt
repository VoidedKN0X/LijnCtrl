package com.vknxworks.lijnctrl.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.towns.TownRepository
import com.vknxworks.lijnctrl.ui.components.LoadContent
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.rememberLoader
import com.vknxworks.lijnctrl.ui.theme.Gutter

/** Every De Lijn stop in one town or village, A to Z, with a filter. */
@Composable
fun TownScreen(number: String, name: String) {
    val c = LocalContext.current.container
    val loader = rememberLoader("town-$number") {
        c.api.municipalityStops(number)
            .distinctBy { it.entity to it.number }
            .sortedWith(compareBy<com.vknxworks.lijnctrl.data.api.ApiStop>({ TownRepository.fold(it.name ?: it.number) }, { it.number }))
    }
    var filter by rememberSaveable { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(name) },
    ) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { all ->
            val f = TownRepository.fold(filter.trim())
            val shown = if (f.isEmpty()) all else all.filter { TownRepository.fold(it.name ?: "").contains(f) || it.number.startsWith(f) }
            Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it.take(60) },
                    placeholder = { Text(stringResource(R.string.filter_stops)) },
                    leadingIcon = { Icon(Icons.Outlined.FilterList, null) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
                )
                Text(
                    pluralStringResource(R.plurals.stops_in_town, all.size, all.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                )
                if (shown.isEmpty()) {
                    MessageView(Icons.Outlined.SearchOff, stringResource(R.string.nothing_found), stringResource(R.string.try_fewer_or_different_words))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(shown, key = { "${it.entity}_${it.number}" }) { s ->
                            StopResultRow(s.entity, s.number, s.name ?: s.number, s.municipality, null)
                        }
                    }
                }
            }
        }
    }
}
