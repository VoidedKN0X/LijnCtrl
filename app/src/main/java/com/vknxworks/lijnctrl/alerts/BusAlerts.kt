package com.vknxworks.lijnctrl.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.alerts.Notifications.privateOnLockScreen
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.departures.DepartureRepository
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/** One bus or tram to alert for at one stop, and how many minutes before it leaves. */
@Serializable
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

/** A set alert, as kept on the phone until it goes off: the request and the departure's last known time. */
@Serializable
data class BusAlert(
    val request: BusAlertRequest,
    /** Departure at the stop, epoch seconds: live when [live], else the timetable. */
    val departureSec: Long,
    val live: Boolean,
    val misses: Int = 0,
) {
    val id: String get() = "${request.entity}_${request.line}_${request.direction}_${request.trip}_${request.stop}"
    val departure: Instant get() = Instant.ofEpochSecond(departureSec)
}

/**
 * "Your bus leaves in X minutes": alerts set from a departure, kept on the phone and woken up by Android's alarm
 * clock (no service runs in between). Each wake-up refreshes the departure's live time, moves the alert with it
 * (see [AlertTiming]) and finally posts the alert, or says the trip was cancelled.
 *
 * Exact alarms are used when Android allows them for LijnCtrl; otherwise `setAndAllowWhileIdle`, which Android may
 * deliver a little later (the alert then says how many minutes are really left). LijnCtrl asks for no extra
 * permission for this.
 *
 * With the app lock on, the API key can't be read in the background once LijnCtrl locks. A copy is kept in memory
 * while alerts are set ([keyCopy]), as the old watching service did; if Android has ended the app since, the alert
 * still goes off at the last known time, without refreshing it.
 */
object BusAlerts {
    private const val PREFS = "bus_alerts"
    private const val KEY = "alerts"
    const val ACTION_CHECK = "com.vknxworks.lijnctrl.alerts.CHECK"
    private const val EXTRA_ID = "id"
    private const val NOTIFY_BASE = 4300

    @Volatile private var keyCopy: String? = null

    private val listSerializer = ListSerializer(BusAlert.serializer())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<BusAlert> = runCatching {
        prefs(context).getString(KEY, null)?.let { DeLijnApi.JSON.decodeFromString(listSerializer, it) }
    }.getOrNull().orEmpty()

    @Synchronized
    private fun save(context: Context, list: List<BusAlert>) {
        prefs(context).edit().apply {
            if (list.isEmpty()) remove(KEY) else putString(KEY, DeLijnApi.JSON.encodeToString(listSerializer, list))
        }.commit()
        if (list.isEmpty()) keyCopy = null
    }

    @Synchronized
    private fun put(context: Context, alert: BusAlert) = save(context, all(context).filter { it.id != alert.id } + alert)

    @Synchronized
    private fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    /** The API key was removed or replaced: drop the alerts' copy of it (alerts then go off at their last known time). */
    fun forgetKey() {
        keyCopy = null
    }

    fun find(context: Context, req: BusAlertRequest): BusAlert? {
        val id = BusAlert(req, 0, false).id
        return all(context).firstOrNull { it.id == id }
    }

    /** Sets (or replaces) the alert for [req], with the departure's current time. Returns what happens next. */
    fun set(context: Context, req: BusAlertRequest, departure: Instant, live: Boolean): AlertTiming.Step {
        context.container.apiKey.key.value?.let { keyCopy = it }
        Notifications.ensureChannels(context)
        val alert = BusAlert(req, departure.epochSecond, live)
        put(context, alert)
        return apply(context, alert, AlertTiming.next(Instant.now(), req.minutesBefore, departure))
    }

    fun cancel(context: Context, req: BusAlertRequest) {
        val id = BusAlert(req, 0, false).id
        alarm(context)?.cancel(pending(context, id))
        remove(context, id)
    }

    /** After the app starts (and after a restart of the phone, alarms are gone): set every stored alert's alarm again. */
    fun rescheduleAll(context: Context) {
        val now = Instant.now()
        all(context).forEach { a ->
            // Only alarms are set here; anything due is decided by the alarm's own check, so nothing goes off twice.
            val step = AlertTiming.next(now, a.request.minutesBefore, a.departure)
            schedule(context, a.id, (step as? AlertTiming.Step.CheckAt)?.at ?: now)
        }
    }

    /** One wake-up: refresh the departure and decide. Runs on a background thread, within a few seconds. */
    internal suspend fun check(context: Context, id: String) = checking.withLock { checkLocked(context, id) }

    /** One check at a time, so an alert can't go off twice when two wake-ups meet. */
    private val checking = Mutex()

    private suspend fun checkLocked(context: Context, id: String) {
        val alert = all(context).firstOrNull { it.id == id } ?: return
        val req = alert.request
        val key = context.container.apiKey.key.value ?: keyCopy
        val now = Instant.now()
        if (key.isNullOrBlank()) {
            // Locked, and the app was ended since the alert was set: no refresh, the last known time stands.
            apply(context, alert, AlertTiming.next(now, req.minutesBefore, alert.departure))
            return
        }
        val list = withTimeoutOrNull(15_000) {
            runCatching { DepartureRepository(DeLijnApi(context.container.http) { key }).upcoming(StopKey(req.entity, req.stop), 30) }.getOrNull()
        }
        if (list == null) {
            apply(context, alert, AlertTiming.whenNoData(Instant.now(), req.minutesBefore, alert.departure))
            return
        }
        val line = LineKey(req.entity, req.line)
        val d = list.firstOrNull { it.line == line && it.trip == req.trip && (it.direction == null || it.direction == req.direction) }
        val at = Instant.now()
        if (d == null) {
            val (step, misses) = AlertTiming.whenMissing(at, req.minutesBefore, alert.departure, alert.misses)
            apply(context, alert.copy(misses = misses), step)
            return
        }
        val fresh = alert.copy(departureSec = d.time.epochSecond, live = d.live, misses = 0)
        apply(context, fresh, AlertTiming.next(at, req.minutesBefore, d.time, d.cancelled))
    }

    private fun apply(context: Context, alert: BusAlert, step: AlertTiming.Step): AlertTiming.Step {
        val req = alert.request
        when (step) {
            is AlertTiming.Step.CheckAt -> {
                put(context, alert)
                schedule(context, alert.id, step.at)
            }
            is AlertTiming.Step.Notify -> {
                remove(context, alert.id)
                val text = if (step.minutesLeft <= 0) str(R.string.bus_alert_now_x, req.lineLabel, req.stopName)
                else str(if (alert.live) R.string.bus_alert_leaves_live_x else R.string.bus_alert_leaves_x, req.stopName, step.minutesLeft)
                post(context, alert, text, loud = true)
            }
            AlertTiming.Step.Cancelled -> {
                remove(context, alert.id)
                post(context, alert, str(R.string.bus_alert_cancelled_x, req.lineLabel, req.destination), loud = true)
            }
            AlertTiming.Step.Left -> {
                remove(context, alert.id)
                post(context, alert, str(R.string.bus_alert_gone_x, req.lineLabel, req.stopName), loud = false)
            }
            AlertTiming.Step.Lost -> {
                remove(context, alert.id)
                post(context, alert, str(R.string.bus_alert_not_found_x, req.lineLabel, req.stopName), loud = false)
            }
        }
        return step
    }

    private fun alarm(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, BusAlertReceiver::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, id: String, at: Instant) {
        val am = alarm(context) ?: return
        val pi = pending(context, id)
        val ms = at.toEpochMilli()
        // Exact when Android allows it for LijnCtrl; otherwise allowed-while-idle, which may come a little late.
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }.onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi) } }
    }

    private fun post(context: Context, alert: BusAlert, text: String, loud: Boolean) {
        if (!Notifications.allowed(context)) return
        Notifications.ensureChannels(context)
        val req = alert.request
        val channel = if (loud) Notifications.BUS else Notifications.WATCH
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(str(R.string.bus_alert_title_x, req.lineLabel, req.destination.ifBlank { req.direction }))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(Notifications.openApp(context, 1))
            .privateOnLockScreen(context, channel, str(R.string.notify_public_bus))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIFY_BASE + (alert.id.hashCode() and 0xFF), n) }
    }

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal const val EXTRA = EXTRA_ID
}

/** Android's alarm for a bus alert: refreshes and decides in the background (not exported: only LijnCtrl's alarms reach it). */
class BusAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BusAlerts.ACTION_CHECK) return
        val id = intent.getStringExtra(BusAlerts.EXTRA) ?: return
        val app = context.applicationContext
        val result = goAsync()
        BusAlerts.scope.launch {
            try {
                BusAlerts.check(app, id)
            } catch (_: Exception) {
                // Never crash in the background; the alert's next alarm (if any) is already set.
            } finally {
                result.finish()
            }
        }
    }
}
