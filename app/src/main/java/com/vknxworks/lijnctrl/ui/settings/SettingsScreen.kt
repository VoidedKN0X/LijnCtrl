package com.vknxworks.lijnctrl.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.lock.LockVault
import com.vknxworks.lijnctrl.data.settings.AppSettings
import com.vknxworks.lijnctrl.data.settings.LockTimeout
import com.vknxworks.lijnctrl.data.settings.MessageLanguage
import com.vknxworks.lijnctrl.data.settings.NearbyRadius
import com.vknxworks.lijnctrl.data.settings.ThemeChoice
import com.vknxworks.lijnctrl.data.settings.TimeStyle
import com.vknxworks.lijnctrl.ui.components.Group
import com.vknxworks.lijnctrl.ui.components.GroupChoice
import com.vknxworks.lijnctrl.ui.components.GroupRow
import com.vknxworks.lijnctrl.ui.components.GroupSwitch
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.lock.AppLock
import com.vknxworks.lijnctrl.ui.lock.PinRequest
import com.vknxworks.lijnctrl.ui.lock.findActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.appearance)) {
                GroupChoice(stringResource(R.string.theme), ThemeChoice.entries.map { it to stringResource(it.label) }, s.theme) { v -> update { it.copy(theme = v) } }
                GroupSwitch(stringResource(R.string.use_wallpaper_colours), stringResource(R.string.replaces_the_default_colours_with_ones), s.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }

            Group(stringResource(R.string.departures), footer = stringResource(R.string.departures_footer)) {
                GroupChoice(
                    stringResource(R.string.show_times_as),
                    TimeStyle.entries.map { it to stringResource(it.label) },
                    s.timeStyle,
                    icon = Icons.Outlined.Schedule,
                ) { v -> update { it.copy(timeStyle = v) } }
                GroupChoice(
                    stringResource(R.string.disruption_language),
                    MessageLanguage.entries.map { it to it.label },
                    s.messageLanguage,
                    icon = Icons.Outlined.Translate,
                ) { v -> update { it.copy(messageLanguage = v) } }
            }

            Group(stringResource(R.string.tab_nearby), footer = stringResource(R.string.nearby_settings_footer)) {
                GroupChoice(
                    stringResource(R.string.nearby_distance),
                    NearbyRadius.entries.map { it to stringResource(R.string.within_x_m, it.metres) },
                    s.nearbyRadius,
                    icon = Icons.Outlined.NearMe,
                ) { v -> update { it.copy(nearbyRadius = v) } }
            }

            Group(stringResource(R.string.tab_search), footer = stringResource(R.string.address_search_footer)) {
                GroupSwitch(
                    stringResource(R.string.address_search),
                    stringResource(R.string.address_search_subtitle),
                    s.addressSearch,
                    icon = Icons.Outlined.Place,
                ) { v -> update { it.copy(addressSearch = v) } }
            }

            Group(stringResource(R.string.route_planner), footer = stringResource(R.string.route_planner_footer)) {
                GroupSwitch(
                    stringResource(R.string.use_transitous),
                    stringResource(R.string.use_transitous_subtitle),
                    s.transitousRoutes,
                    icon = Icons.Outlined.Directions,
                ) { v -> update { it.copy(transitousRoutes = v) } }
                if (s.transitousRoutes) {
                    GroupRow(
                        stringResource(R.string.transitous_sources),
                        stringResource(R.string.transitous_sources_subtitle),
                        icon = Icons.Outlined.Info,
                        showChevron = true,
                        onClick = {
                            runCatching {
                                AppLock.expectReturn()
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.vknxworks.lijnctrl.data.plan.TransitousPlanner.SOURCES_URL))
                                        .addCategory(android.content.Intent.CATEGORY_BROWSABLE),
                                )
                            }
                        },
                    )
                }
            }

            PrivacyGroup(s) { t -> update(t) }
        }
    }
}

/** Privacy and security: the shared Ctrl series app lock and screenshot protection. */
@Composable
private fun PrivacyGroup(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(
        stringResource(R.string.group_privacy_security),
        footer = if (lockOn) stringResource(R.string.app_lock_footer) else null,
    ) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                s.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { v -> update { it.copy(lockTimeout = v) } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            s.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { v -> update { it.copy(hideScreenshots = v) } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            s.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { v -> update { it.copy(hideRecents = v) } }
    }
}
