package com.vknxworks.lijnctrl.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.vknxworks.lijnctrl.MainActivity
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.ui.nav.LaunchRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

// Ctrl / Signal colours. Glance can't read the Compose theme, so each one is a day/night pair that follows the
// phone's dark/light setting on the home screen, like the app.
private val WSurface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF141417))
private val WBorder = ColorProvider(day = Color(0xFFDEDED7), night = Color(0xFF232328))
private val WInk = ColorProvider(day = Color(0xFF0B0B0D), night = Color(0xFFF4F4F2))
private val WMuted = ColorProvider(day = Color(0xFF5C5C64), night = Color(0xFFA3A3AB))
private val WInactive = ColorProvider(day = Color(0xFF74747C), night = Color(0xFF8C8C94))
private val WDivider = ColorProvider(day = Color(0xFFE6E6E0), night = Color(0xFF1E1E23))
private val WAccent = ColorProvider(day = Color(0xFF6A3BDB), night = Color(0xFFB48CFF))
private val WError = ColorProvider(day = Color(0xFFD93636), night = Color(0xFFFF6B6B))
private val WBadge = ColorProvider(day = Color(0xFFECECE7), night = Color(0xFF1C1C21))

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(ms: Long): String = CLOCK.format(Instant.ofEpochMilli(ms).atZone(BRUSSELS))

private fun fixed(hex: String?) = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let {
    val c = Color(0xFF000000 or it)
    ColorProvider(day = c, night = c)
}

/** Opens the stop's page in LijnCtrl (through the app lock). Each widget gets its own URI, so their taps don't mix. */
private fun openStopIntent(context: Context, appWidgetId: Int, cfg: WidgetConfig): Intent =
    LaunchRequests.sign(
        context,
        Intent(context, MainActivity::class.java)
            .setAction(LaunchRequests.ACTION_OPEN_STOP)
            .setData(Uri.parse("lijnctrl-widget://stop/$appWidgetId"))
            .putExtra(LaunchRequests.EXTRA_ENTITY, cfg.entity)
            .putExtra(LaunchRequests.EXTRA_STOP, cfg.number)
            .putExtra(LaunchRequests.EXTRA_NAME, cfg.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun openAppIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("lijnctrl-widget://open/$appWidgetId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

private fun configureIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, DepartureWidgetConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setData(Uri.parse("lijnctrl-widget://configure/$appWidgetId"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The refresh button: asks De Lijn again now (taps closer together than 20 seconds count once). */
class RefreshDeparturesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Widgets.refreshNow(context)
    }
}

private enum class WidgetState { READY, LOCKED, NO_KEY }

/** Everything one widget draws. */
private data class WidgetView(val state: WidgetState, val cfg: WidgetConfig?, val snapshot: WidgetSnapshot?)

/**
 * Reads what the widget should show right now. With the app lock on and LijnCtrl locked, the key is sealed:
 * nothing about your stop is shown, not even the last departures, until you unlock the app.
 */
private suspend fun loadView(context: Context, appWidgetId: Int): WidgetView {
    val c = context.container
    val state = when {
        !c.apiKey.hasKey.value -> WidgetState.NO_KEY
        c.apiKey.key.value == null -> WidgetState.LOCKED
        else -> WidgetState.READY
    }
    val cfg = if (state == WidgetState.READY) runCatching { WidgetStore.config(context, appWidgetId) }.getOrNull() else null
    val snapshot = if (cfg != null) runCatching { WidgetStore.snapshot(context, appWidgetId) }.getOrNull() else null
    return WidgetView(state, cfg, snapshot)
}

/** "Next departures" home-screen widget for one of your saved stops. */
class DepartureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val initial = loadView(context, appWidgetId)
        provideContent {
            // A widget that's still showing keeps this composition, so new departures, a new stop or the app
            // locking only appear when they're read again: every redraw bumps this number to make that happen.
            val version = currentState<Preferences>()[Widgets.REDRAW] ?: 0L
            var view by remember { mutableStateOf(initial) }
            LaunchedEffect(version) { view = loadView(context, appWidgetId) }
            Content(appWidgetId, view.state, view.cfg, view.snapshot)
        }
    }
}

@Composable
private fun Frame(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(WBorder).padding(1.dp),
    ) {
        Box(GlanceModifier.fillMaxSize().cornerRadius(21.dp).background(WSurface).padding(horizontal = 14.dp, vertical = 10.dp)) {
            content()
        }
    }
}

private val labelStyle = TextStyle(color = WMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)

@Composable
private fun Content(appWidgetId: Int, state: WidgetState, cfg: WidgetConfig?, s: WidgetSnapshot?) {
    val context = LocalContext.current
    when {
        state != WidgetState.READY -> Frame(GlanceModifier.clickable(actionStartActivity(openAppIntent(context, appWidgetId)))) {
            Message(
                context.getString(if (state == WidgetState.LOCKED) R.string.widget_locked else R.string.widget_no_key),
                context.getString(if (state == WidgetState.LOCKED) R.string.widget_locked_body else R.string.widget_no_key_body),
            )
        }
        cfg == null -> Frame(GlanceModifier.clickable(actionStartActivity(configureIntent(context, appWidgetId)))) {
            Message(context.getString(R.string.widget_name), context.getString(R.string.widget_pick_stop))
        }
        else -> Frame {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openStopIntent(context, appWidgetId, cfg)))) {
                        val updated = s?.updatedAt?.takeIf { it > 0 }?.let { context.getString(R.string.widget_updated_x, clock(it)) }
                        Text(
                            listOfNotNull(context.getString(R.string.stop_number_x, cfg.number), updated).joinToString(" · ").uppercase(),
                            style = labelStyle,
                            maxLines = 1,
                        )
                        Text(cfg.name, style = TextStyle(color = WInk, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Image(
                        ImageProvider(R.drawable.ic_widget_refresh),
                        context.getString(R.string.refresh),
                        GlanceModifier.size(36.dp).padding(8.dp).clickable(actionRunCallback<RefreshDeparturesAction>()),
                        colorFilter = ColorFilter.tint(WMuted),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                val now = System.currentTimeMillis()
                // Departures that already left since the last refresh are left out.
                val list = s?.departures.orEmpty().filter { it.cancelled || it.time > now - 60_000 }
                val open = actionStartActivity(openStopIntent(context, appWidgetId, cfg))
                when {
                    s == null -> Message(null, context.getString(R.string.loading_departures))
                    list.isEmpty() -> Message(null, s.error ?: context.getString(R.string.no_departures_soon))
                    else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(list.indices.toList(), itemId = { it.toLong() }) { i ->
                            Column(GlanceModifier.fillMaxWidth().clickable(open)) {
                                if (i > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WDivider)) {}
                                DepartureRow(context, list[i])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String?, body: String) {
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) Text(title, style = TextStyle(color = WInk, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(body, style = TextStyle(color = WMuted, fontSize = 12.sp), maxLines = 3)
    }
}

@Composable
private fun DepartureRow(context: Context, d: WidgetDeparture) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        // The line number in De Lijn's own colours, as on the bus and the stop sign.
        Box(
            GlanceModifier.width(40.dp).cornerRadius(7.dp).background(fixed(d.background) ?: WBadge).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                d.line,
                style = TextStyle(color = fixed(d.foreground) ?: WInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                d.destination.ifBlank { context.getString(R.string.unknown_destination) },
                style = TextStyle(
                    color = if (d.cancelled) WMuted else WInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
            )
            if (d.disrupted) {
                Text(context.getString(R.string.widget_disruption), style = TextStyle(color = WError, fontSize = 10.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val delay = d.delayMinutes
            if (d.cancelled) {
                Text(context.getString(R.string.cancelled).uppercase(), style = TextStyle(color = WError, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), maxLines = 1)
                Text(
                    clock(d.scheduled),
                    style = TextStyle(color = WMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, textDecoration = TextDecoration.LineThrough),
                    maxLines = 1,
                )
            } else {
                // Clock times, not "4 min": a widget isn't redrawn every minute, so a countdown would go stale.
                Text(
                    (if (d.live) "■ " else "") + clock(d.time),
                    style = TextStyle(color = if (d.live) WAccent else WInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
                Text(
                    when {
                        delay == null -> context.getString(R.string.timetable)
                        delay >= 1 -> context.getString(R.string.x_min_late, delay.toInt())
                        delay <= -1 -> context.getString(R.string.x_min_early, (-delay).toInt())
                        else -> context.getString(R.string.on_time)
                    }.uppercase(),
                    style = TextStyle(
                        color = when {
                            delay != null && delay >= 3 -> WError
                            delay == null -> WInactive
                            else -> WMuted
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

class DepartureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DepartureWidget()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext
        scope.launch { runCatching { WidgetStore.remove(app, appWidgetIds) } }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Widgets.cancel(context)
    }
}
