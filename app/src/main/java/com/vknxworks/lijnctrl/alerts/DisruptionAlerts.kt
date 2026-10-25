package com.vknxworks.lijnctrl.alerts

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import com.vknxworks.lijnctrl.data.appDataStore
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.flow.first
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
    private val SEEN = stringSetPreferencesKey("disruptions_seen")

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
        if (c.apiKey.key.value.isNullOrBlank()) return
        val language = c.settings.settings.value.messageLanguage.key
        val found = linkedMapOf<String, Pair<String, String>>() // id -> (where, title + text)
        // In a fresh process the saved lists load from disk a moment after start: wait for them briefly.
        val stops = kotlinx.coroutines.withTimeoutOrNull(3_000) { c.favourites.stops.first { it.isNotEmpty() } } ?: c.favourites.stops.value
        val lines = kotlinx.coroutines.withTimeoutOrNull(1_000) { c.favourites.lines.first { it.isNotEmpty() } } ?: c.favourites.lines.value

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

        val store = context.applicationContext.appDataStore
        val seen = store.data.first()[SEEN].orEmpty()
        val new = found.filterKeys { it !in seen }
        // Only what's still going on is remembered, so the list doesn't grow forever.
        store.edit { it[SEEN] = found.keys }
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
                .build()
            nm.notify(id.hashCode(), n)
        }
    }
}

class DisruptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { DisruptionAlerts.check(applicationContext, quiet = false) }
        return Result.success()
    }
}
