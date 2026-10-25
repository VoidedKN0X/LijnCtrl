package com.vknxworks.lijnctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system), DARK(R.string.theme_dark), LIGHT(R.string.theme_light), BLACK(R.string.theme_black),
}

/** How long the app can be in the background before the app lock asks again. -1: only after a restart. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/** How departure times are written. */
enum class TimeStyle(@StringRes val label: Int) {
    /** "Now", "4 min", and the clock time once it's more than an hour away. */
    MINUTES(R.string.time_style_minutes),
    /** "14:32". */
    CLOCK(R.string.time_style_clock),
}

/** Language for disruption messages; [key] is the name De Lijn uses in its translations. */
enum class MessageLanguage(val key: String, val label: String) {
    ENGLISH("engels", "English"),
    DUTCH("nederlands", "Nederlands"),
    FRENCH("frans", "Français"),
    GERMAN("duits", "Deutsch"),
}

/** How far the Nearby tab looks for stops. */
enum class NearbyRadius(val metres: Int) {
    M500(500), M750(750), M1000(1000), M1500(1500), M2000(2000),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    val timeStyle: TimeStyle = TimeStyle.MINUTES,
    val messageLanguage: MessageLanguage = MessageLanguage.ENGLISH,
    /** Distance for Nearby, the list and the map's "Search this area". */
    val nearbyRadius: NearbyRadius = NearbyRadius.M1000,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** Hide in screenshots: FLAG_SECURE on the app's windows (which also blanks the recent-apps preview). */
    val hideScreenshots: Boolean = false,
    /** Hide in recent apps: the recent-apps view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val timeStyle = stringPreferencesKey("time_style")
        val language = stringPreferencesKey("message_language")
        val radius = stringPreferencesKey("nearby_radius")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        timeStyle = enumOr(this[K.timeStyle], TimeStyle.MINUTES),
        messageLanguage = enumOr(this[K.language], MessageLanguage.ENGLISH),
        nearbyRadius = enumOr(this[K.radius], NearbyRadius.M1000),
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.timeStyle] = s.timeStyle.name
            p[K.language] = s.messageLanguage.name
            p[K.radius] = s.nearbyRadius.name
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
