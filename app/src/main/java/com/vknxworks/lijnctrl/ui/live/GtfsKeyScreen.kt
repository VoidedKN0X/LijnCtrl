package com.vknxworks.lijnctrl.ui.live

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.live.LiveVehicles
import com.vknxworks.lijnctrl.ui.components.PillButton
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.lock.SecureScreen
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * The optional GTFS Realtime key, for live bus and tram positions on the maps. A separate subscription on
 * data.delijn.be from the main key; checked against De Lijn before it's saved, and stored encrypted like it.
 */
@Composable
fun GtfsKeyScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    SecureScreen()
    val hasKey by c.gtfsKey.hasKey.collectAsStateWithLifecycle()

    var key by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var checking by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save() {
        val k = key.trim()
        if (k.isEmpty() || checking) return
        focus.clearFocus()
        checking = true
        error = null
        scope.launch {
            runCatching { c.live.check(k) }
                .onSuccess {
                    c.gtfsKey.set(k)
                    key = ""
                    nav.back()
                }
                .onFailure { error = it.friendlyMessage() }
            checking = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.live_vehicles)) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.gtfs_key_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Card, color = MaterialTheme.colorScheme.surface, border = com.vknxworks.lijnctrl.ui.theme.CardBorder, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.gtfs_key_steps), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                AppLock.expectReturn()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LiveVehicles.PORTAL_URL)).addCategory(Intent.CATEGORY_BROWSABLE))
                            }
                        },
                        shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                        border = com.vknxworks.lijnctrl.ui.theme.ControlBorder,
                        colors = com.vknxworks.lijnctrl.ui.components.ctrlSecondaryButtonColors(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.open_de_lijn_portal))
                    }
                }
            }
            if (hasKey) {
                Text(stringResource(R.string.gtfs_key_saved), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim(); error = null },
                label = { Text(stringResource(if (hasKey) R.string.gtfs_key_replace else R.string.gtfs_key)) },
                singleLine = true,
                enabled = !checking,
                isError = error != null,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
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
            PillButton(
                if (checking) stringResource(R.string.checking_key) else stringResource(R.string.check_and_save),
                onClick = ::save,
                enabled = key.isNotBlank() && !checking,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasKey) {
                TextButton(onClick = { c.gtfsKey.clear() }) {
                    Text(stringResource(R.string.gtfs_key_remove), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.gtfs_key_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
