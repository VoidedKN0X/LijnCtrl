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

    /**
     * Looks once. The first look after turning alerts on only remembers what's already going on ([quiet]), so you
     * aren't flooded with old news; after that, each new disruption is one notification.
     */
    suspend fun check(context: Context, quiet: Boolean) {
        val c = context.applicationContext.container
        if (c.apiKey.current().isNullOrBlank()) return
        val language = c.settings.settings.value.messageLanguage.key
        val found = linkedMapOf<String, Pair<String, String>>() // id -> (where, title + text)
        // In a fresh process the saved lists are read (and decrypted) a moment after start: wait for them. If they
        // can't be had (not read in time, or the saved file can't be opened), nothing is checked this time.
        kotlinx.coroutines.withTimeoutOrNull(10_000) { c.saved.awaitReady() } ?: return
        if (c.saved.unreadable.value) return
        val stops = c.saved.data.value.stops
        val lines = c.saved.data.value.lines

        for (stop in stops) {
            val list = runCatching { c.api.stopDisruptions(stop.key) }.getOrDefault(emptyList()) +
                runCatching { c.api.stopDiversions(stop.key) }.getOrDefault(emptyList())
            list.forEach { d ->
                val l = d.localized(language)
                found.getOrPut("s:${stop.key}:${(l.title + l.text).hashCode()}") { stop.title to listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("\n") }
            }
        }
        for (saved in lines) {
            val key = LineKey(saved.entity, saved.line)
            val directions = runCatching { c.api.lineDirections(key) }.getOrDefault(emptyList()).mapNotNull { it.direction }.distinct()
            directions.forEach { dir ->
                runCatching { c.api.lineDisruptions(key, dir) }.getOrDefault(emptyList()).forEach { d ->
                    val l = d.localized(language)
                    found.getOrPut("l:$key:${(l.title + l.text).hashCode()}") {
                        str(R.string.line_x, saved.publicNumber) to listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("\n")
                    }
                }
            }
        }

        val seen = c.saved.data.value.seen
        val new = found.filterKeys { it !in seen }
        // Only what's still going on is remembered, so the list doesn't grow forever.
        val now = found.keys.toSet()
        c.saved.update { it.copy(seen = now) }
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
