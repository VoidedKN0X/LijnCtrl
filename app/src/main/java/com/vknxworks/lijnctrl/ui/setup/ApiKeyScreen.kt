package com.vknxworks.lijnctrl.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiException
import com.vknxworks.lijnctrl.str
import com.vknxworks.lijnctrl.ui.components.PillButton
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.lock.SecureScreen
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val PORTAL_URL = "https://data.delijn.be/"

/**
 * Asks for the De Lijn API key, checks it against both APIs it needs, and saves it. Shown on first start,
 * when the saved key can't be read any more, and from the You page to change it.
 */
@Composable
fun ApiKeyScreen(firstRun: Boolean, unreadable: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // The key is a secret: keep it out of screenshots, recordings and the recent-apps preview while it's typed.
    SecureScreen()

    var key by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var checking by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // First start only: the optional live-bus key and whether to use Transitous, so setup is one screen.
    var gtfs by rememberSaveable { mutableStateOf("") }
    var gtfsError by rememberSaveable { mutableStateOf<String?>(null) }
    var useTransitous by rememberSaveable { mutableStateOf(true) }

    fun openPortal() {
        runCatching {
            AppLock.expectReturn()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL_URL)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
    }

    fun check() {
        val k = key.trim()
        if (k.isEmpty() || checking) return
        focus.clearFocus()
        checking = true
        error = null
        scope.launch {
            // Both APIs are checked: departures come from the core API, stop search from the search API.
            val core = async { runCatching { c.api.entities(key = k) } }
            val search = async { runCatching { c.api.searchStops("Gent", max = 1, key = k) } }
            val coreResult = core.await()
            val searchResult = search.await()
            val coreErr = coreResult.exceptionOrNull()
            val searchErr = searchResult.exceptionOrNull()
            checking = false
            error = when {
                coreErr == null && searchErr == null -> null
                coreErr is ApiException && searchErr is ApiException && coreErr.code == 401 && searchErr.code == 401 ->
                    str(R.string.key_refused_by_both)
                coreErr != null && searchErr == null -> str(R.string.key_missing_core, coreErr.friendlyMessage())
                coreErr == null && searchErr != null -> str(R.string.key_missing_search, searchErr.friendlyMessage())
                else -> (coreErr ?: searchErr)!!.friendlyMessage()
            }
            if (error == null && firstRun && gtfs.isNotBlank()) {
                checking = true
                gtfsError = runCatching { c.live.check(gtfs) }.exceptionOrNull()?.friendlyMessage()
                checking = false
                if (gtfsError != null) return@launch
                c.gtfsKey.set(gtfs)
                gtfs = ""
            }
            if (error == null) {
                if (firstRun) c.settings.update { it.copy(useTransitous = useTransitous) }
                c.apiKey.set(k)
                key = ""
                onDone()
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onCancel != null) {
                    Row(Modifier.fillMaxWidth()) {
                        com.vknxworks.lijnctrl.ui.components.CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onCancel)
                    }
                } else {
                    Spacer(Modifier.height(32.dp))
                }
                Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.vknxworks.lijnctrl.ui.components.IconTile(Icons.Outlined.DirectionsBus, size = 72.dp, accent = true)
                    Spacer(Modifier.height(20.dp))
                    com.vknxworks.lijnctrl.ui.components.CtrlWordmark()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(if (firstRun) R.string.welcome_title else R.string.change_api_key),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (unreadable) R.string.key_unreadable_intro else R.string.key_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))

                    Surface(shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Card, color = MaterialTheme.colorScheme.surface, border = com.vknxworks.lijnctrl.ui.theme.CardBorder, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.key_steps_title), style = MaterialTheme.typography.titleMedium)
                            Step(1, stringResource(R.string.key_step_1))
                            Step(2, stringResource(R.string.key_step_2))
                            Step(3, stringResource(R.string.key_step_3))
                            OutlinedButton(onClick = ::openPortal, shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control, border = com.vknxworks.lijnctrl.ui.theme.ControlBorder, colors = com.vknxworks.lijnctrl.ui.components.ctrlSecondaryButtonColors()) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.open_de_lijn_portal))
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.trim(); error = null },
                        label = { Text(stringResource(R.string.api_key)) },
                        singleLine = true,
                        enabled = !checking,
                        isError = error != null,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { check() }),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    stringResource(if (visible) R.string.hide_key else R.string.show_key),
                                )
                            }
                        },
                        supportingText = { Text(error ?: stringResource(R.string.key_stored_note)) },
                        shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                        colors = com.vknxworks.lijnctrl.ui.components.ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (firstRun) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Card, color = MaterialTheme.colorScheme.surface, border = com.vknxworks.lijnctrl.ui.theme.CardBorder, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.setup_optional_title), style = MaterialTheme.typography.titleMedium)
                                // Transitous: route planning and address search.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.use_transitous), style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(R.string.setup_transitous_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.size(12.dp))
                                    com.vknxworks.lijnctrl.ui.components.CtrlSwitch(checked = useTransitous, onCheckedChange = { useTransitous = it }, enabled = !checking)
                                }
                                // Live buses: the second, optional key.
                                Text(stringResource(R.string.live_vehicles), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.setup_gtfs_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = gtfs,
                                    onValueChange = { gtfs = it.trim(); gtfsError = null },
                                    label = { Text(stringResource(R.string.gtfs_key_optional)) },
                                    singleLine = true,
                                    enabled = !checking,
                                    isError = gtfsError != null,
                                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { check() }),
                                    supportingText = { gtfsError?.let { Text(it) } },
                                    shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                                    colors = com.vknxworks.lijnctrl.ui.components.ctrlFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PillButton(
                        if (checking) stringResource(R.string.checking_key) else stringResource(R.string.check_and_save),
                        onClick = ::check,
                        enabled = key.isNotBlank() && !checking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (checking) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.key_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Badge).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = com.vknxworks.lijnctrl.ui.theme.CtrlType.Data, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}
