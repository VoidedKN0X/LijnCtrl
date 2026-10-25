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
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.departures.DepartureRepository
import com.vknxworks.lijnctrl.data.live.LiveTarget
import com.vknxworks.lijnctrl.data.live.LiveVehicles
import com.vknxworks.lijnctrl.data.plan.Journey
import com.vknxworks.lijnctrl.data.plan.LegMode
import com.vknxworks.lijnctrl.data.plan.distanceMetres
import com.vknxworks.lijnctrl.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/** One part of a guided journey, in a form that can be handed to the service. */
@Serializable
data class GuideLeg(
    val walk: Boolean,
    val mode: String,
    val fromName: String,
    val toName: String,
    val start: Long,
    val end: Long,
    val scheduledStart: Long? = null,
    val badge: String? = null,
    val headsign: String? = null,
    val lineEntity: String? = null,
    val lineNumber: String? = null,
    val direction: String? = null,
    val trip: String? = null,
    val serviceDate: String? = null,
    val fromEntity: String? = null,
    val fromNumber: String? = null,
    val toEntity: String? = null,
    val toNumber: String? = null,
    val toLat: Double? = null,
    val toLon: Double? = null,
    val track: String? = null,
)

fun Journey.toGuide(): List<GuideLeg> = legs.map { l ->
    GuideLeg(
        walk = l.isWalk,
        mode = l.mode.name,
        fromName = l.fromName,
        toName = l.toName,
        start = l.start.epochSecond,
        end = l.end.epochSecond,
        scheduledStart = l.scheduledStart?.epochSecond,
        badge = l.badge,
        headsign = l.headsign,
        lineEntity = l.line?.entity,
        lineNumber = l.line?.line,
        direction = l.direction,
        trip = l.trip,
        serviceDate = l.serviceDate?.toString(),
        fromEntity = l.fromStop?.entity,
        fromNumber = l.fromStop?.number,
        toEntity = l.toStop?.entity,
        toNumber = l.toStop?.number,
        toLat = l.path.lastOrNull()?.latitude,
        toLon = l.path.lastOrNull()?.longitude,
        track = l.track,
    )
}

/**
 * Trip guidance: follows a planned route and tells you, in one ongoing notification, what's next — when to leave,
 * when your bus or tram is about to arrive, and when to get off — with an alert (sound and vibration) at each of
 * those moments. De Lijn rides use live times from your stops (and the vehicle's live position, with a GTFS
 * Realtime key, to know when you're nearly there); other rides (trains) follow the planned times.
 *
 * Like the bus alert, it keeps its own copy of the keys while it runs, so it works with the app closed or locked.
 */
class TripGuideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val legs = intent?.getStringExtra(EXTRA_LEGS)?.let {
            runCatching { DeLijnApi.JSON.decodeFromString(ListSerializer(GuideLeg.serializer()), it) }.getOrNull()
        }
        val c = applicationContext.container
        val key = c.apiKey.key.value
        Notifications.ensureChannels(this)
        if (legs.isNullOrEmpty() || key.isNullOrBlank()) {
            stopEverything()
            return START_NOT_STICKY
        }
        val destination = legs.last().toName
        startForeground(ONGOING_ID, ongoing(destination, str(R.string.guide_starting)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        _running.value = true
        job?.cancel()
        job = scope.launch { guide(legs, key, c.gtfsKey.key.value) }
        return START_NOT_STICKY
    }

    /** A moment to alert about, and when it is. */
    private data class Moment(val id: String, val at: Instant, val title: String, val text: String)

    private suspend fun guide(legs: List<GuideLeg>, key: String, gtfsKey: String?) {
        val c = applicationContext.container
        val api = DeLijnApi(c.http) { key }
        val departures = DepartureRepository(api)
        val live = gtfsKey?.let { k -> LiveVehicles(c.http) { k } }
        val destination = legs.last().toName
        val rides = legs.withIndex().filter { !it.value.walk }
        // Live times found so far, per ride: when it leaves your stop and when it gets to the stop you get off at.
        val depLive = mutableMapOf<Int, Instant>()
        val arrLive = mutableMapOf<Int, Instant>()
        val matched = mutableMapOf<Int, Departure>()
        val fired = mutableSetOf<String>()
        val started = System.currentTimeMillis()

        while (System.currentTimeMillis() - started < MAX_MS) {
            val now = Instant.now()

            // Live times for the ride that's next or under way (and the one after, a little before).
            val current = rides.firstOrNull { (i, l) -> (arrLive[i] ?: Instant.ofEpochSecond(l.end)).isAfter(now.minusSeconds(60)) }
            if (current == null) {
                val lastEnd = Instant.ofEpochSecond(legs.last().end)
                if (now.isAfter(lastEnd.minusSeconds(60)) || rides.isEmpty()) {
                    finish(destination, str(R.string.guide_arrived_x, destination))
                    return
                }
            }
            current?.let { (i, l) -> refresh(i, l, departures, c, depLive, arrLive, matched) }

            val moments = moments(legs, depLive, arrLive)
            // "Get off soon" a little early when the vehicle itself is seen close to your stop.
            if (live != null && current != null) {
                val (i, l) = current
                val dep = depLive[i] ?: Instant.ofEpochSecond(l.start)
                val lat = l.toLat
                val lon = l.toLon
                if (now.isAfter(dep) && lat != null && lon != null && "alight-$i" !in fired && l.toNumber != null) {
                    val arr = arrLive[i] ?: Instant.ofEpochSecond(l.end)
                    val fix = runCatching { live.locate(LiveTarget(mapOf(l.toNumber to arr), matched[i]?.trip ?: l.trip)) }.getOrNull()
                    if (fix != null && distanceMetres(fix.latitude, fix.longitude, lat, lon) < NEAR_M) {
                        fired += "alight-$i"
                        alert(str(R.string.guide_get_off_title_x, l.toName), str(R.string.guide_get_off_next_x, l.toName))
                    }
                }
            }

            // Alerts for moments that have come, once each; the ongoing notification shows the next one.
            moments.filter { !it.at.isAfter(now) && it.id !in fired }.forEach { m ->
                fired += m.id
                // Old moments (more than 3 minutes past, e.g. right after starting) aren't worth a loud alert.
                if (m.at.isAfter(now.minusSeconds(180))) alert(m.title, m.text)
            }
            val next = moments.firstOrNull { it.at.isAfter(now) && it.id !in fired }
            val status = next?.let { m ->
                val mins = Math.floorDiv(m.at.epochSecond - now.epochSecond + 59, 60L).toInt()
                str(R.string.guide_next_x, m.title, mins)
            } ?: str(R.string.guide_almost_there_x, destination)
            notify(ONGOING_ID, ongoing(destination, status))

            val soon = next != null && next.at.epochSecond - now.epochSecond < 240
            delay(if (soon) 15_000 else 30_000)
        }
        finish(destination, str(R.string.bus_alert_timeout))
    }

    /** Live departure at the boarding stop and live arrival at the stop you get off at, for De Lijn rides. */
    private suspend fun refresh(
        i: Int,
        l: GuideLeg,
        departures: DepartureRepository,
        c: com.vknxworks.lijnctrl.AppContainer,
        depLive: MutableMap<Int, Instant>,
        arrLive: MutableMap<Int, Instant>,
        matched: MutableMap<Int, Departure>,
    ) {
        val from = if (l.fromEntity != null && l.fromNumber != null) StopKey(l.fromEntity, l.fromNumber) else return
        val planned = Instant.ofEpochSecond(l.scheduledStart ?: l.start)
        val now = Instant.now()
        // Before (or just after) it leaves: its live time at your stop.
        if ((depLive[i] ?: Instant.ofEpochSecond(l.start)).isAfter(now.minusSeconds(120))) {
            val list = runCatching { departures.upcoming(from, 30) }.getOrNull().orEmpty()
            runCatching { c.lines.ensure(list.mapNotNull { it.line }.toSet()) }
            val d = list.firstOrNull { d -> sameRide(d, l, c, planned) }
            if (d != null) {
                matched[i] = d
                depLive[i] = d.time
            }
        }
        // On board: when it gets to the stop you get off at.
        val m = matched[i]
        if (m != null && l.toEntity != null && l.toNumber != null) {
            val list = runCatching { departures.upcoming(StopKey(l.toEntity, l.toNumber), 30) }.getOrNull().orEmpty()
            list.firstOrNull { it.line == m.line && it.trip == m.trip }?.let { arrLive[i] = it.time }
                ?: run {
                    // Not listed at that stop: keep the planned arrival, moved by the delay seen at boarding.
                    val delay = (depLive[i]?.epochSecond ?: l.start) - l.start
                    arrLive[i] = Instant.ofEpochSecond(l.end + delay)
                }
        }
    }

    /** The departure that is this ride: the same trip when known, else the same line number around the planned time. */
    private fun sameRide(d: Departure, l: GuideLeg, c: com.vknxworks.lijnctrl.AppContainer, planned: Instant): Boolean {
        if (l.trip != null && l.lineNumber != null) return d.trip == l.trip && d.line?.line == l.lineNumber
        val number = d.line?.let { c.lines.get(it)?.publicNumber ?: it.line } ?: return false
        return number.equals(l.badge, ignoreCase = true) && kotlin.math.abs(d.scheduled.epochSecond - planned.epochSecond) <= 180
    }

    /** Leave, board and get-off moments for the whole journey, with live times where known. */
    private fun moments(legs: List<GuideLeg>, depLive: Map<Int, Instant>, arrLive: Map<Int, Instant>): List<Moment> {
        val out = mutableListOf<Moment>()
        val firstRide = legs.indexOfFirst { !it.walk }
        if (firstRide < 0) return emptyList()
        val first = legs[firstRide]
        val dep0 = depLive[firstRide] ?: Instant.ofEpochSecond(first.start)
        val walkBefore = legs.take(firstRide).sumOf { it.end - it.start }
        if (walkBefore > 0) {
            out += Moment("leave", dep0.minusSeconds(walkBefore + 60), str(R.string.guide_leave_title), str(R.string.guide_leave_x, first.fromName, (walkBefore / 60).toInt()))
        }
        legs.forEachIndexed { i, l ->
            if (l.walk) return@forEachIndexed
            val what = l.badge?.let { str(rideNoun(l.mode), it) } ?: str(R.string.guide_your_ride)
            val dep = depLive[i] ?: Instant.ofEpochSecond(l.start)
            val arr = arrLive[i] ?: Instant.ofEpochSecond(l.end)
            val toward = l.headsign?.let { " " + str(R.string.to_x, it) }.orEmpty()
            val platform = l.track?.let { " · " + str(R.string.platform_x, it) }.orEmpty()
            out += Moment("board-$i", dep.minusSeconds(120), str(R.string.guide_board_title_x, what + toward), str(R.string.guide_board_x, what, l.fromName) + platform)
            out += Moment("alight-$i", arr.minusSeconds(120), str(R.string.guide_get_off_title_x, l.toName), str(R.string.guide_get_off_x, l.toName))
        }
        return out.sortedBy { it.at }
    }

    private fun rideNoun(mode: String) = when (mode) {
        LegMode.TRAM.name -> R.string.guide_tram_x
        LegMode.TRAIN.name -> R.string.guide_train_x
        LegMode.METRO.name -> R.string.guide_metro_x
        else -> R.string.guide_bus_x
    }

    private fun ongoing(destination: String, status: String): Notification =
        NotificationCompat.Builder(this, Notifications.WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(str(R.string.guide_title_x, destination))
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(Notifications.openApp(this, 5))
            .addAction(0, str(R.string.stop_watching), stopIntent(this))
            .build()

    private fun alert(title: String, text: String) {
        val n = NotificationCompat.Builder(this, Notifications.BUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(Notifications.openApp(this, 6))
            .build()
        notify(ALERT_ID, n)
    }

    private fun finish(destination: String, text: String) {
        if (Notifications.allowed(this)) {
            notify(
                ALERT_ID,
                NotificationCompat.Builder(this, Notifications.WATCH)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(str(R.string.guide_title_x, destination))
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openApp(this, 7))
                    .build(),
            )
        }
        stopEverything()
    }

    private fun notify(id: Int, n: Notification) {
        if (!Notifications.allowed(this)) return
        getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    private fun stopEverything() {
        job?.cancel()
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.vknxworks.lijnctrl.alerts.GUIDE_STOP"
        private const val EXTRA_LEGS = "legs"
        private const val ONGOING_ID = 4301
        private const val ALERT_ID = 4302
        private const val MAX_MS = 4 * 60 * 60 * 1000L
        /** The vehicle counts as nearly at your stop within this distance. */
        private const val NEAR_M = 450.0

        private val _running = MutableStateFlow(false)
        /** Guidance is on (for the route page's Start/Stop button). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context, journey: Journey) {
            val json = DeLijnApi.JSON.encodeToString(ListSerializer(GuideLeg.serializer()), journey.toGuide())
            ContextCompat.startForegroundService(context, Intent(context, TripGuideService::class.java).putExtra(EXTRA_LEGS, json))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TripGuideService::class.java).setAction(ACTION_STOP))
        }

        private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            8,
            Intent(context, TripGuideService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
