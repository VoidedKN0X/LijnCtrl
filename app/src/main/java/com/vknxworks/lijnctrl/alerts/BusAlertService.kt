package com.vknxworks.lijnctrl.alerts

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.departures.DepartureRepository
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** One bus or tram to watch at one stop, and how many minutes before it gets there to alert. */
data class BusAlertRequest(
    val entity: String,
    val stop: String,
    val stopName: String,
    val line: String,
    val lineLabel: String,
    val direction: String,
    val trip: String,
    val destination: String,
    val minutesBefore: Int,
)

/**
 * "Your bus is almost here": watches one bus or tram's live time at your stop, shown as a quiet ongoing
 * notification, and alerts (sound and vibration) when it's [BusAlertRequest.minutesBefore] minutes away. Then it
 * stops by itself; it also stops when the bus has gone, after three hours, or when you tap Stop.
 *
 * It holds its own copy of the API key while it runs, so it keeps working after the app lock locks the app.
 * The copy only lives in this service's memory and is dropped when it stops.
 */
class BusAlertService : Service() {
    // Anything unexpected while watching ends the alert quietly instead of crashing the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> stopEverything() })
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val req = intent?.let(::requestFrom)
        val key = applicationContext.container.apiKey.key.value
        Notifications.ensureChannels(this)
        // Started with startForegroundService: Android requires the foreground notification first, even when the
        // service stops straight away (otherwise it ends the app). If Android refuses, it just stops.
        val first = if (req != null) ongoing(req, str(R.string.bus_alert_looking)) else placeholder()
        if (!goForeground(first) || req == null || key.isNullOrBlank()) {
            stopEverything()
            return START_NOT_STICKY
        }
        job?.cancel()
        job = scope.launch { watch(req, key) }
        return START_NOT_STICKY
    }

    private suspend fun watch(req: BusAlertRequest, key: String) {
        val api = DeLijnApi(applicationContext.container.http) { key }
        val departures = DepartureRepository(api)
        val stop = StopKey(req.entity, req.stop)
        val line = LineKey(req.entity, req.line)
        val started = System.currentTimeMillis()
        var misses = 0
        var lastSeen: Instant? = null
        while (System.currentTimeMillis() - started < MAX_MS) {
            val list = runCatching { departures.upcoming(stop, 30) }.getOrNull()
            val d = list?.firstOrNull { it.line == line && it.trip == req.trip && (it.direction == null || it.direction == req.direction) }
            val now = Instant.now()
            if (d == null) {
                // Not (or no longer) listed: it has left if we saw it before and its time has passed.
                val seen = lastSeen
                if (seen != null && seen.isBefore(now.plusSeconds(60))) {
                    finish(req, str(R.string.bus_alert_gone_x, req.lineLabel, req.stopName))
                    return
                }
                if (list != null && ++misses >= 4) {
                    finish(req, str(R.string.bus_alert_not_found_x, req.lineLabel, req.stopName))
                    return
                }
                delay(30_000)
                continue
            }
            misses = 0
            lastSeen = d.time
            if (d.cancelled) {
                finish(req, str(R.string.bus_alert_cancelled_x, req.lineLabel, req.destination), loud = true)
                return
            }
            val minutes = Math.floorDiv(d.time.epochSecond - now.epochSecond, 60L).toInt()
            if (minutes <= req.minutesBefore) {
                val text = if (minutes <= 0) str(R.string.bus_alert_now_x, req.lineLabel, req.stopName)
                else str(R.string.bus_alert_due_x, req.lineLabel, minutes, req.stopName)
                finish(req, text, loud = true)
                return
            }
            val status = if (d.live) str(R.string.bus_alert_status_live_x, minutes) else str(R.string.bus_alert_status_timetable_x, minutes)
            notify(ONGOING_ID, ongoing(req, status))
            // Closer to the alert, look more often.
            delay(if (minutes - req.minutesBefore <= 3) 15_000 else 30_000)
        }
        finish(req, str(R.string.bus_alert_timeout))
    }

    private fun ongoing(req: BusAlertRequest, status: String): Notification =
        NotificationCompat.Builder(this, Notifications.WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(this.getColor(R.color.notification_accent))
            .setContentTitle(str(R.string.bus_alert_title_x, req.lineLabel, req.destination))
            .setContentText(str(R.string.bus_alert_text_x, status, req.minutesBefore, req.stopName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifications.openApp(this))
            .addAction(0, str(R.string.stop_watching), stopIntent(this))
            .build()

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, Notifications.WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.bus_alert_looking))
            .setSilent(true)
            .build()

    private fun goForeground(n: Notification): Boolean = try {
        startForeground(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        true
    } catch (e: Exception) {
        false
    }

    /** Android 15+: the daily time allowed for this kind of service is used up; stop before Android ends the app. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopEverything()
    }

    /** The final notification (the alert itself, or why watching stopped), then the service ends. */
    private fun finish(req: BusAlertRequest, text: String, loud: Boolean = false) {
        if (Notifications.allowed(this)) {
            val n = NotificationCompat.Builder(this, if (loud) Notifications.BUS else Notifications.WATCH)
                .setSmallIcon(R.drawable.ic_notification)
            .setColor(this.getColor(R.color.notification_accent))
                .setContentTitle(str(R.string.bus_alert_title_x, req.lineLabel, req.destination))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(Notifications.openApp(this, 1))
                .build()
            notify(DONE_ID, n)
        }
        stopEverything()
    }

    private fun notify(id: Int, n: Notification) {
        if (!Notifications.allowed(this)) return
        getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    private fun stopEverything() {
        job?.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.vknxworks.lijnctrl.alerts.STOP"
        private const val ONGOING_ID = 4201
        private const val DONE_ID = 4202
        private const val MAX_MS = 3 * 60 * 60 * 1000L

        fun start(context: Context, req: BusAlertRequest) {
            val i = Intent(context, BusAlertService::class.java)
                .putExtra("entity", req.entity)
                .putExtra("stop", req.stop)
                .putExtra("stopName", req.stopName)
                .putExtra("line", req.line)
                .putExtra("lineLabel", req.lineLabel)
                .putExtra("direction", req.direction)
                .putExtra("trip", req.trip)
                .putExtra("destination", req.destination)
                .putExtra("minutes", req.minutesBefore)
            // Only refused when the app isn't on screen, which a tap on "Alert me" rules out; never crash over it.
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, BusAlertService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun requestFrom(i: Intent): BusAlertRequest? {
            fun s(k: String) = i.getStringExtra(k)
            return BusAlertRequest(
                entity = s("entity") ?: return null,
                stop = s("stop") ?: return null,
                stopName = s("stopName") ?: return null,
                line = s("line") ?: return null,
                lineLabel = s("lineLabel") ?: return null,
                direction = s("direction") ?: return null,
                trip = s("trip") ?: return null,
                destination = s("destination").orEmpty(),
                minutesBefore = i.getIntExtra("minutes", 5),
            )
        }
    }
}
