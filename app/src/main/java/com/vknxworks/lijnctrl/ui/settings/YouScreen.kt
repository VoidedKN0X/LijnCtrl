package com.vknxworks.lijnctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.ui.components.ConfirmDialog
import com.vknxworks.lijnctrl.ui.components.CtrlButton
import com.vknxworks.lijnctrl.ui.components.CtrlLabel
import com.vknxworks.lijnctrl.ui.components.CtrlSquareButton
import com.vknxworks.lijnctrl.ui.components.Group
import com.vknxworks.lijnctrl.ui.components.GroupRow
import com.vknxworks.lijnctrl.ui.components.IconTile
import com.vknxworks.lijnctrl.ui.nav.ApiKeyRoute
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nav.SettingsRoute
import com.vknxworks.lijnctrl.ui.theme.CtrlType
import com.vknxworks.lijnctrl.ui.theme.Gutter
import com.vknxworks.lijnctrl.ui.lock.AppLock

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
        // Close on the left, a mono "YOU" label in the middle, as on every Ctrl profile page.
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.back), onClick = nav::back, modifier = Modifier.align(Alignment.CenterStart))
            CtrlLabel(stringResource(R.string.you))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Outlined.DirectionsBus, size = 68.dp, accent = true)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "api.delijn.be · " + androidx.compose.ui.res.pluralStringResource(R.plurals.saved_stops_count, saved.size, saved.size),
                        style = CtrlType.Data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Group(stringResource(R.string.de_lijn_api), footer = stringResource(R.string.api_key_footer)) {
                GroupRow(
                    stringResource(R.string.change_api_key),
                    stringResource(R.string.api_key_saved_encrypted),
                    icon = Icons.Outlined.Key,
                    showChevron = true,
                    onClick = { nav.navigate(ApiKeyRoute) },
                )
                GroupRow(
                    stringResource(R.string.de_lijn_portal),
                    stringResource(R.string.de_lijn_portal_subtitle),
                    icon = Icons.Outlined.Public,
                    showChevron = true,
                    onClick = { open(PORTAL_URL) },
                )
                GroupRow(
                    stringResource(R.string.remove_api_key),
                    icon = Icons.Outlined.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmRemove = true },
                )
            }

            Group(stringResource(R.string.group_app)) {
                GroupRow(stringResource(R.string.settings), icon = Icons.Outlined.Tune, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }

            Group(stringResource(R.string.group_about), footer = stringResource(R.string.data_credit)) {
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Outlined.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Outlined.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Outlined.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }

            // Donations: the one accent button on the page.
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CtrlButton(stringResource(R.string.support_title), onClick = { open(SPONSOR_URL) }, icon = Icons.Outlined.FavoriteBorder, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = CtrlType.DataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                "GOOGLE-FREE · TELEMETRY-FREE · ARM64",
                style = CtrlType.DataSmall.copy(letterSpacing = 0.12.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
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
