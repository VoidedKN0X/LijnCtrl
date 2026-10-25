package com.vknxworks.lijnctrl

import android.content.Context
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.auth.ApiKeyStore
import com.vknxworks.lijnctrl.data.departures.DepartureRepository
import com.vknxworks.lijnctrl.data.favourites.FavouritesRepository
import com.vknxworks.lijnctrl.data.towns.TownRepository
import com.vknxworks.lijnctrl.data.plan.TransitousPlanner
import com.vknxworks.lijnctrl.data.lines.LineRepository
import com.vknxworks.lijnctrl.data.lock.LockVault
import com.vknxworks.lijnctrl.data.settings.SettingsRepository
import com.vknxworks.lijnctrl.ui.lock.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Only ever talks to De Lijn's API, over HTTPS: any other host, plain HTTP or a redirect elsewhere is
     * refused before the request (and the API key in its header) leaves the phone.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "LijnCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build()
            )
        }
        .addNetworkInterceptor(Interceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host != DeLijnApi.HOST) throw IOException(str(R.string.blocked_request_to_x, url.host))
            chain.proceed(chain.request())
        })
        .build()

    val settings = SettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that seals the API key while locked. */
    val vault = LockVault(context, "lijnctrl")

    val apiKey = ApiKeyStore(context, vault)
    val api = DeLijnApi(http) { apiKey.key.value }

    /** The optional GTFS Realtime key (a separate De Lijn subscription), for live vehicle positions. Stored like [apiKey]. */
    val gtfsKey = ApiKeyStore(context, vault, "gtfs_key")
    val live = com.vknxworks.lijnctrl.data.live.LiveVehicles(http) { gtfsKey.key.value }
    val lines = LineRepository(context, api)
    val departures = DepartureRepository(api)
    /** Disruptions and diversions per stop and line direction, cached for a few minutes. */
    val disruptions = com.vknxworks.lijnctrl.data.disruptions.DisruptionRepository(api)
    val towns = TownRepository(api)
    /** Route planning and address search; only contacted once the user turns on Transitous. */
    val transitous = TransitousPlanner()
    val favourites = FavouritesRepository(context, appScope)
    val planHistory = com.vknxworks.lijnctrl.data.plan.PlanHistoryRepository(context, appScope)
    val places = com.vknxworks.lijnctrl.data.plan.SavedPlacesRepository(context, appScope)

    /** The app lock is on. */
    val lockEnabled: Boolean get() = vault.enabled

    /** Turns the app lock on with [pin]: the PIN seals the API key from now on. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        apiKey.seal()
        gtfsKey.seal()
    }

    /** Turns the app lock off (after the PIN was confirmed): the API key goes back to Keystore-only encryption. */
    suspend fun disableLock() {
        apiKey.unseal()
        gtfsKey.unseal()
        vault.disable()
    }

    /** "Forgot PIN?": the sealed API key can't be read any more, so forget it. Saved stops stay. */
    suspend fun forgetEverything() {
        apiKey.clear()
        gtfsKey.clear()
        AppLock.markUnlocked()
        vault.disable()
    }

    /** Locked: wipe the vault key and the readable API key from memory. */
    fun onLocked() {
        vault.close()
        apiKey.onLocked()
        gtfsKey.onLocked()
        // The departures widgets stop showing your stop while the key is sealed.
        if (vault.enabled) appScope.launch { runCatching { com.vknxworks.lijnctrl.widget.Widgets.redraw(context) } }
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        apiKey.onUnlocked()
        gtfsKey.onUnlocked()
        // Unlocked: the departures widgets can show (and fetch) departures again.
        if (vault.enabled) appScope.launch {
            runCatching { if (com.vknxworks.lijnctrl.widget.Widgets.placed(context).isNotEmpty()) com.vknxworks.lijnctrl.widget.Widgets.refreshNow(context) }
        }
    }

    init {
        AppLock.onLocked = { onLocked() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as LijnCtrlApp).container
