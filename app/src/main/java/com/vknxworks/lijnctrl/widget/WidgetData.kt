package com.vknxworks.lijnctrl.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** What one departures widget shows: a saved stop and, optionally, only some of its lines. */
@Serializable
data class WidgetConfig(
    val entity: String,
    val number: String,
    val name: String,
    /** Lines to show, as "entity_line"; empty shows every line. */
    val lines: List<String> = emptyList(),
) {
    val stop: StopKey get() = StopKey(entity, number)
}

/** One departure as the widget draws it: the badge, where it's going and when, live or from the timetable. */
@Serializable
data class WidgetDeparture(
    val line: String,
    /** De Lijn's line colours, hex without '#'. */
    val foreground: String? = null,
    val background: String? = null,
    val destination: String,
    val scheduled: Long,
    val predicted: Long? = null,
    val cancelled: Boolean = false,
    val live: Boolean = false,
    /** A disruption or diversion at the stop affects it. */
    val disrupted: Boolean = false,
) {
    val time: Long get() = predicted ?: scheduled
    val delayMinutes: Long? get() = predicted?.let { Math.round((it - scheduled) / 60_000.0) }
}

@Serializable
data class WidgetSnapshot(
    val updatedAt: Long = 0L,
    val departures: List<WidgetDeparture> = emptyList(),
    /** The last refresh failed; [departures] are from the refresh before. */
    val error: String? = null,
)

@Serializable
private data class WidgetFile(
    val configs: Map<String, WidgetConfig> = emptyMap(),
    val snapshots: Map<String, WidgetSnapshot> = emptyMap(),
)

/** The widgets' settings and last departures, in one small file in the app's private storage. */
object WidgetStore {
    private val mutex = Mutex()
    private fun file(context: Context) = File(context.applicationContext.filesDir, "widgets.json")

    private fun read(context: Context): WidgetFile = runCatching {
        DeLijnApi.JSON.decodeFromString(WidgetFile.serializer(), file(context).readText())
    }.getOrDefault(WidgetFile())

    private fun write(context: Context, data: WidgetFile) {
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(DeLijnApi.JSON.encodeToString(WidgetFile.serializer(), data))
        tmp.renameTo(f)
    }

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }

    suspend fun config(context: Context, appWidgetId: Int): WidgetConfig? = locked { read(context).configs[appWidgetId.toString()] }

    suspend fun snapshot(context: Context, appWidgetId: Int): WidgetSnapshot? = locked { read(context).snapshots[appWidgetId.toString()] }

    suspend fun configs(context: Context): Map<Int, WidgetConfig> = locked {
        read(context).configs.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap()
    }

    /** A new stop or line choice: the old departures are dropped so they're never shown for the wrong stop. */
    suspend fun setConfig(context: Context, appWidgetId: Int, config: WidgetConfig) = locked {
        val d = read(context)
        write(context, d.copy(configs = d.configs + (appWidgetId.toString() to config), snapshots = d.snapshots - appWidgetId.toString()))
    }

    suspend fun setSnapshots(context: Context, snapshots: Map<Int, WidgetSnapshot>) = locked {
        val d = read(context)
        write(context, d.copy(snapshots = d.snapshots + snapshots.mapKeys { it.key.toString() }))
    }

    suspend fun remove(context: Context, appWidgetIds: IntArray) = locked {
        val d = read(context)
        val ids = appWidgetIds.map { it.toString() }.toSet()
        write(context, d.copy(configs = d.configs - ids, snapshots = d.snapshots - ids))
    }

    /** Forgets widgets that aren't on the home screen any more. */
    suspend fun keepOnly(context: Context, appWidgetIds: Set<Int>) = locked {
        val d = read(context)
        val keep = appWidgetIds.map { it.toString() }.toSet()
        if (d.configs.keys.all { it in keep } && d.snapshots.keys.all { it in keep }) return@locked
        write(context, d.copy(configs = d.configs.filterKeys { it in keep }, snapshots = d.snapshots.filterKeys { it in keep }))
    }
}

/**
 * Refreshes the departures widgets: every 15 minutes while one is placed (the shortest WorkManager allows, and
 * gentle on your De Lijn API quota), after a tap on the refresh button, when a widget is set up and when the
 * app is unlocked. One call per stop, however many widgets show it.
 */
object Widgets {
    private const val PERIODIC = "departure-widgets"
    private const val NOW = "departure-widgets-now"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private val refreshing = Mutex()

    /** How many departures a widget shows at most. */
    const val SHOWN = 5

    /** A manual refresh at most this often, so a burst of taps doesn't use up the API quota. */
    private const val MIN_MANUAL_GAP_MS = 20_000L
    @Volatile private var lastManual = 0L

    suspend fun placed(context: Context): List<Pair<GlanceId, Int>> = runCatching {
        val m = GlanceAppWidgetManager(context)
        m.getGlanceIds(DepartureWidget::class.java).map { it to m.getAppWidgetId(it) }
    }.getOrDefault(emptyList())

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<DepartureWidgetWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Refreshes soon (taps closer together than 20 seconds count once). */
    fun refreshNow(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastManual < MIN_MANUAL_GAP_MS) return
        lastManual = now
        val req = OneTimeWorkRequestBuilder<DepartureWidgetWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    /** Bumped on every redraw, so a widget that's still showing reads its stop and departures again. */
    val REDRAW = longPreferencesKey("redraw")

    suspend fun redraw(context: Context) {
        val app = context.applicationContext
        for ((glanceId, _) in placed(app)) {
            try {
                updateAppWidgetState(app, glanceId) { it[REDRAW] = (it[REDRAW] ?: 0L) + 1 }
                DepartureWidget().update(app, glanceId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    /** Fetches departures for every placed widget and redraws them. */
    suspend fun update(context: Context) = refreshing.withLock {
        val app = context.applicationContext
        val ids = placed(app)
        if (ids.isEmpty()) {
            cancel(app)
            return@withLock
        }
        WidgetStore.keepOnly(app, ids.map { it.second }.toSet())
        val c = app.container
        // No key, or it's sealed with the app PIN while LijnCtrl is locked: the widgets say so instead.
        if (c.apiKey.key.value.isNullOrBlank()) {
            redraw(app)
            return@withLock
        }
        val configs = WidgetStore.configs(app).filterKeys { id -> ids.any { it.second == id } }
        val language = c.settings.settings.value.messageLanguage.key
        val byStop = mutableMapOf<StopKey, Result<Pair<List<Departure>, Set<String>>>>()
        for (stop in configs.values.map { it.stop }.distinct()) {
            byStop[stop] = try {
                val list = c.departures.upcoming(stop, max = 30)
                val disrupted = runCatching { c.disruptions.forDepartures(stop, list, language, withLines = false) }.getOrDefault(emptyMap())
                Result.success(list to disrupted.keys)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
        val lineKeys = byStop.values.flatMap { r -> r.getOrNull()?.first.orEmpty().mapNotNull { it.line } }.toSet()
        runCatching { c.lines.ensure(lineKeys) }
        val now = System.currentTimeMillis()
        val snapshots = configs.mapValues { (id, cfg) ->
            val r = byStop[cfg.stop]
            val got = r?.getOrNull()
            if (got == null) {
                val old = WidgetStore.snapshot(app, id) ?: WidgetSnapshot()
                old.copy(
                    departures = old.departures.filter { it.time > now - 60_000 },
                    error = r?.exceptionOrNull()?.friendlyMessage() ?: old.error,
                )
            } else {
                val (list, disrupted) = got
                WidgetSnapshot(
                    updatedAt = now,
                    departures = list
                        .filter { cfg.lines.isEmpty() || it.line?.toString() in cfg.lines }
                        .take(SHOWN)
                        .map { d -> d.toWidget(d.line?.let { c.lines.get(it) }, d.matchKey in disrupted) },
                )
            }
        }
        WidgetStore.setSnapshots(app, snapshots)
        redraw(app)
    }

    private fun Departure.toWidget(info: com.vknxworks.lijnctrl.data.lines.LineInfo?, disrupted: Boolean) = WidgetDeparture(
        line = info?.publicNumber ?: line?.line.orEmpty(),
        foreground = info?.foreground,
        background = info?.background,
        destination = destination,
        scheduled = scheduled.toEpochMilli(),
        predicted = predicted?.toEpochMilli(),
        cancelled = cancelled,
        live = live,
        disrupted = disrupted,
    )
}

class DepartureWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            Widgets.update(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            runCatching { Widgets.redraw(applicationContext) }
        }
        return Result.success()
    }
}
