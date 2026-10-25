package com.vknxworks.lijnctrl.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.ui.components.CtrlButton
import com.vknxworks.lijnctrl.ui.components.CtrlChip
import com.vknxworks.lijnctrl.ui.components.CtrlDivider
import com.vknxworks.lijnctrl.ui.components.CtrlLabel
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.lock.AppLockOverlay
import com.vknxworks.lijnctrl.ui.lock.ScreenPrivacy
import com.vknxworks.lijnctrl.ui.lock.WindowPrivacy
import com.vknxworks.lijnctrl.ui.lock.lockCovering
import com.vknxworks.lijnctrl.ui.lock.lockLocked
import com.vknxworks.lijnctrl.ui.theme.CardBorder
import com.vknxworks.lijnctrl.ui.theme.CtrlShapes
import com.vknxworks.lijnctrl.ui.theme.Gutter
import com.vknxworks.lijnctrl.ui.theme.LijnCtrlTheme
import kotlinx.coroutines.launch

/**
 * Picks the saved stop (and, optionally, the lines) a departures widget shows. Opens when the widget is added,
 * and again from the widget's settings on launchers that offer it. Behind the app lock, like the app itself,
 * and it follows Hide in screenshots and Hide in recent apps.
 */
class DepartureWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget unconfigured, so the launcher doesn't add it.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setRecentsScreenshotEnabled(false)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(0xFF08080A.toInt()) else SystemBarStyle.light(0xFFF5F5F1.toInt(), 0xFF08080A.toInt())
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }
            val covered = lockCovering(c.vault)
            WindowPrivacy(this, settings.hideScreenshots || covered || ScreenPrivacy.forced > 0, hideRecents = settings.hideRecents || c.vault.enabled)
            val locked = lockLocked(c.vault)
            LijnCtrlTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    if (!locked) {
                        Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                            Picker(appWidgetId, onDone = ::done, onCancel = ::finish)
                        }
                    }
                    AppLockOverlay()
                }
            }
        }
    }

    private fun done(config: WidgetConfig) {
        val app = applicationContext
        val id = appWidgetId
        container.appScope.launch {
            WidgetStore.setConfig(app, id, config)
            runCatching { DepartureWidget().update(app, GlanceAppWidgetManager(app).getGlanceIdBy(id)) }
            Widgets.schedule(app)
            runCatching { Widgets.update(app) }
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}

@Composable
private fun Picker(appWidgetId: Int, onDone: (WidgetConfig) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val hasKey by c.apiKey.hasKey.collectAsStateWithLifecycle()
    val saved by c.favourites.stops.collectAsStateWithLifecycle()
    var stop by remember { mutableStateOf<SavedStop?>(null) }
    var lines by remember { mutableStateOf<Set<String>>(emptySet()) }
    var served by remember { mutableStateOf<List<LineKey>>(emptyList()) }

    // Reconfiguring: start from what the widget shows now.
    LaunchedEffect(appWidgetId) {
        WidgetStore.config(context, appWidgetId)?.let { cfg ->
            stop = c.favourites.stops.value.firstOrNull { it.key == cfg.stop } ?: SavedStop(cfg.entity, cfg.number, cfg.name)
            lines = cfg.lines.toSet()
        }
    }
    LaunchedEffect(stop?.key) {
        val k = stop?.key ?: return@LaunchedEffect
        served = emptyList()
        served = runCatching { c.api.stopLines(k) }.getOrDefault(emptyList()).map { LineKey(it.entity, it.line) }.distinct()
    }
    val info = rememberLineInfo(served)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 20.dp),
            )
            Text(
                stringResource(R.string.widget_config_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasKey -> MessageView(Icons.Outlined.StarBorder, stringResource(R.string.widget_no_key), stringResource(R.string.widget_no_key_body))
                    saved.isEmpty() -> MessageView(Icons.Outlined.StarBorder, stringResource(R.string.no_saved_stops), stringResource(R.string.widget_config_no_stops))
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp)) {
                        item { CtrlLabel(stringResource(R.string.widget_config_stop).uppercase(), Modifier.padding(bottom = 8.dp)) }
                        item {
                            Surface(shape = CtrlShapes.Card, color = MaterialTheme.colorScheme.surface, border = CardBorder, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    saved.forEachIndexed { i, s ->
                                        if (i > 0) CtrlDivider()
                                        val picked = stop?.key == s.key
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                if (!picked) { stop = s; lines = emptySet() }
                                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                if (picked) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                                                null,
                                                tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    listOfNotNull(stringResource(R.string.stop_number_x, s.number), s.municipality).joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (stop != null && served.size > 1) {
                            item {
                                CtrlLabel(stringResource(R.string.widget_config_lines).uppercase(), Modifier.padding(top = 20.dp, bottom = 4.dp))
                                Text(
                                    stringResource(R.string.widget_config_lines_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CtrlChip(selected = lines.isEmpty(), onClick = { lines = emptySet() }, label = { Text(stringResource(R.string.all_lines)) })
                                    served.sortedWith(compareBy<LineKey>({ info[it.toString()]?.publicNumber?.length ?: 9 }, { info[it.toString()]?.publicNumber ?: it.line }))
                                        .forEach { k ->
                                            val id = k.toString()
                                            CtrlChip(
                                                selected = id in lines,
                                                onClick = { lines = if (id in lines) lines - id else lines + id },
                                                label = { LineBadge(info[id], k.line, minWidth = 40.dp) },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                CtrlButton(
                    stringResource(R.string.widget_config_save),
                    onClick = {
                        val s = stop ?: return@CtrlButton
                        // Every line ticked is the same as no filter.
                        val all = served.map { it.toString() }.toSet()
                        val pick = if (lines.isEmpty() || (all.isNotEmpty() && lines.containsAll(all))) emptyList() else lines.sorted()
                        onDone(WidgetConfig(s.entity, s.number, s.title, pick))
                    },
                    enabled = stop != null && hasKey,
                )
            }
        }
    }
}
