package com.vknxworks.lijnctrl.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is, read once from Android's own location service: no Google Play services. Asks every
 * available provider at once (satellite, network, Android's own fused provider) and takes the first answer.
 */
object Locator {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Location is turned off in Android's quick settings. */
    fun isOff(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return true
        return !lm.isLocationEnabled
    }

    /** A recent fix (under two minutes old) if one is known, otherwise a fresh one; null after [timeoutMs]. */
    @Suppress("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 20_000L): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }

        val recent = (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_AGE_NANOS }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent
        if (providers.isEmpty()) return null

        val result = CompletableDeferred<Location?>()
        val signals = providers.map { CancellationSignal() }
        try {
            providers.forEachIndexed { i, provider ->
                runCatching {
                    lm.getCurrentLocation(provider, signals[i], context.mainExecutor) { loc ->
                        if (loc != null) result.complete(loc)
                    }
                }
            }
            return withTimeoutOrNull(timeoutMs) { result.await() }
        } finally {
            signals.forEach { runCatching { it.cancel() } }
        }
    }

    private const val MAX_AGE_NANOS = 2L * 60 * 1_000_000_000
}
