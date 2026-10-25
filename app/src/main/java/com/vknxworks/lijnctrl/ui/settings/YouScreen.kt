package com.vknxworks.lijnctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.ui.components.ConfirmDialog
import com.vknxworks.lijnctrl.ui.components.Group
import com.vknxworks.lijnctrl.ui.components.GroupRow
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.nav.ApiKeyRoute
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nav.SettingsRoute
import com.vknxworks.lijnctrl.ui.theme.Gutter

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/LijnCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val PORTAL_URL = "https://data.delijn.be/"

/** The You page, opened from the profile picture: your API key, settings, and about. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }

    fun open(url: String) {
        runCatching {
            AppLock.expectReturn()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.you))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.DirectionsBus, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.saved_stops_count, saved.size, saved.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Group(stringResource(R.string.de_lijn_api), footer = stringResource(R.string.api_key_footer)) {
                GroupRow(
                    stringResource(R.string.change_api_key),
                    stringResource(R.string.api_key_saved_encrypted),
                    icon = Icons.Filled.Key,
                    showChevron = true,
                    onClick = { nav.navigate(ApiKeyRoute) },
                )
                GroupRow(
                    stringResource(R.string.de_lijn_portal),
                    stringResource(R.string.de_lijn_portal_subtitle),
                    icon = Icons.Filled.Public,
                    showChevron = true,
                    onClick = { open(PORTAL_URL) },
                )
                GroupRow(
                    stringResource(R.string.remove_api_key),
                    icon = Icons.Filled.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmRemove = true },
                )
            }

            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }

            Group(stringResource(R.string.group_about), footer = stringResource(R.string.data_credit)) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), icon = Icons.Filled.Favorite, showChevron = true, onClick = { open(SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Filled.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Filled.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Filled.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            stringResource(R.string.remove_api_key_q),
            stringResource(R.string.remove_api_key_body),
            stringResource(R.string.remove),
            onDismiss = { confirmRemove = false },
        ) {
            confirmRemove = false
            c.apiKey.clear()
        }
    }
}
