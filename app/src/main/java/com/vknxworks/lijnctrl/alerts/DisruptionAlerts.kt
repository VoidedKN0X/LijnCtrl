package com.vknxworks.lijnctrl.alerts

import com.vknxworks.lijnctrl.alerts.Notifications.privateOnLockScreen
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.localized
import com.vknxworks.lijnctrl.str
import java.util.concurrent.TimeUnit

/**
 * Disruption alerts: every half hour (when there's a network), LijnCtrl asks De Lijn for disruptions and
 * diversions on your saved stops and lines, and notifies you of ones it hasn't told you about yet.
 *
 * It needs the API key while the app is closed, so it only works while the app lock is off: with the lock on, the
 * key is sealed with your PIN and can't be read in the background (the check then quietly does nothing).
 */
object DisruptionAlerts {
    private const val WORK = "disruption-alerts"

    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<DisruptionWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    internal fun stopSource(stop: String) = "s:$stop:"
    internal fun lineSource(line: String) = "l:$line:"

    /**
     * What to remember after a look: everything [found], plus what was already remembered for the stops and lines
     * whose request failed this time ([failed], as [stopSource]/[lineSource] prefixes), so a disruption that is
     * still going on isn't announced again after one bad answer. Null when no request worked at all (API down, key
     * expired): then nothing is changed.
     */
    internal fun nextSeen(previous: Set<String>, found: Set<String>, failed: Set<String>, anySucceeded: Boolean): Set<String>? {
        if (!anySucceeded) return null
        val kept = previous.filter { id -> failed.any { id.startsWith(it) } }
        return found + kept
    }

    /**
     * Looks once. The first look after turning alerts on only remembers what's already going on ([quiet]), so you
     * aren't flooded with old news; after that, each new disruption is one notification.
     */
    suspend fun check(context: Context, quiet: Boolean) {
        val c = context.applicationContext.container
        if (c.apiKey.current().isNullOrBlank()) return
        // Read from disk: in a fresh process the settings in memory are still the defaults for a moment.
        val language = c.settings.current().messageLanguage.key
        val found = linkedMapOf<String, Pair<String, String>>() // id -> (where, title + text)
        // In a fresh process the saved lists are read (and decrypted) a moment after start: wait for them. If they
        // can't be had (not read in time, or the saved file can't be opened), nothing is checked this time.
        kotlinx.coroutines.withTimeoutOrNull(10_000) { c.saved.awaitReady() } ?: return
        if (c.saved.unreadable.value) return
        val stops = c.saved.data.value.stops
        val lines = c.saved.data.value.lines
        // Stops and lines whose answer didn't fully come (busy API, timeout): what was already told about them is kept.
        val failed = mutableSetOf<String>()
        var anySucceeded = false
        suspend fun <T> ask(source: String, request: suspend () -> List<T>): List<T> =
            runCatching { request() }
                .onSuccess { anySucceeded = true }
                .onFailure { e -> if (e is kotlin.coroutines.cancellation.CancellationException) throw e; failed += source }
                .getOrDefault(emptyList())

        for (stop in stops) {
            val source = stopSource(stop.key.toString())
            val list = ask(source) { c.api.stopDisruptions(stop.key) } + ask(source) { c.api.stopDiversions(stop.key) }
            list.forEach { d ->
                val l = d.localized(language)
                found.getOrPut("$source${(l.title + l.text).hashCode()}") { stop.title to listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("\n") }
            }
        }
        for (saved in lines) {
            val key = LineKey(saved.entity, saved.line)
            val source = lineSource(key.toString())
            val directions = ask(source) { c.api.lineDirections(key) }.mapNotNull { it.direction }.distinct()
            directions.forEach { dir ->
                ask(source) { c.api.lineDisruptions(key, dir) }.forEach { d ->
                    val l = d.localized(language)
                    found.getOrPut("$source${(l.title + l.text).hashCode()}") {
                        str(R.string.line_x, saved.publicNumber) to listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("\n")
                    }
                }
            }
        }

        val seen = c.saved.data.value.seen
        val next = nextSeen(seen, found.keys, failed, anySucceeded || (stops.isEmpty() && lines.isEmpty())) ?: return
        val new = found.filterKeys { it !in seen }
        // Only what's still going on is remembered, so the list doesn't grow forever.
        if (next != seen) c.saved.update { it.copy(seen = next) }
        if (quiet || new.isEmpty() || !Notifications.allowed(context)) return

        Notifications.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        new.entries.take(5).forEach { (id, v) ->
            val (where, text) = v
            val n = NotificationCompat.Builder(context, Notifications.DISRUPTIONS)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
                .setContentTitle(str(R.string.disruption_at_x, where))
                .setContentText(text.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setGroup("disruptions")
                .setContentIntent(Notifications.openApp(context, 3))
                .privateOnLockScreen(context, Notifications.DISRUPTIONS, str(R.string.channel_disruptions))
                .build()
            nm.notify(id.hashCode(), n)
        }
    }
}

class DisruptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            DisruptionAlerts.check(applicationContext, quiet = false)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        return Result.success()
    }
}
