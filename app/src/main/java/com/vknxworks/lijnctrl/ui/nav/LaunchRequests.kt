package com.vknxworks.lijnctrl.ui.nav

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.lijnctrl.data.plan.SavedPlacesRepository

/** Where the app was asked to open from outside: a launcher shortcut or the home-screen widget. */
sealed interface LaunchTarget {
    /** "Go home" / "Go to work": plan from where you are to that place, now. */
    data class Go(val slot: SavedPlacesRepository.Slot) : LaunchTarget

    /** A stop's page (from the departures widget). */
    data class Stop(val entity: String, val number: String, val name: String?) : LaunchTarget
}

/**
 * Hands a launch request from MainActivity to the screens. It's kept until the app's screens are shown, so a
 * shortcut or widget tap still works when the app lock or the API key screen comes first.
 */
object LaunchRequests {
    const val ACTION_GO_HOME = "com.vknxworks.lijnctrl.action.GO_HOME"
    const val ACTION_GO_TO_WORK = "com.vknxworks.lijnctrl.action.GO_TO_WORK"
    const val ACTION_OPEN_STOP = "com.vknxworks.lijnctrl.action.OPEN_STOP"
    const val EXTRA_ENTITY = "entity"
    const val EXTRA_STOP = "stop"
    const val EXTRA_NAME = "name"
    private const val EXTRA_TOKEN = "lijnctrl_token"

    /**
     * A random secret only LijnCtrl's own shortcuts and widget carry. MainActivity is exported (it's the
     * launcher activity), so without it any app could make LijnCtrl read your location and plan a route.
     */
    private fun token(context: Context): String {
        val prefs = context.getSharedPreferences("launch_token", Context.MODE_PRIVATE)
        return prefs.getString("token", null)
            ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("token", it).commit() }
    }

    /** Marks [intent] as one of LijnCtrl's own. */
    fun sign(context: Context, intent: Intent): Intent = intent.putExtra(EXTRA_TOKEN, token(context))

    var pending by mutableStateOf<LaunchTarget?>(null)

    /** Reads [intent]; anything that isn't one of ours, or has odd stop numbers, is ignored. */
    fun handle(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_GO_HOME && action != ACTION_GO_TO_WORK && action != ACTION_OPEN_STOP) return
        if (intent.getStringExtra(EXTRA_TOKEN) != token(context)) return
        val target = when (action) {
            ACTION_GO_HOME -> LaunchTarget.Go(SavedPlacesRepository.Slot.HOME)
            ACTION_GO_TO_WORK -> LaunchTarget.Go(SavedPlacesRepository.Slot.WORK)
            ACTION_OPEN_STOP -> {
                val entity = intent.getStringExtra(EXTRA_ENTITY)
                val stop = intent.getStringExtra(EXTRA_STOP)
                if (entity != null && stop != null && entity.matches(DIGITS) && stop.matches(DIGITS)) {
                    LaunchTarget.Stop(entity, stop, intent.getStringExtra(EXTRA_NAME)?.take(120))
                } else null
            }
            else -> null
        }
        if (target != null) pending = target
    }

    private val DIGITS = Regex("\\d{1,12}")
}
