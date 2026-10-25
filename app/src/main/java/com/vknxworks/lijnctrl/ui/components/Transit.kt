package com.vknxworks.lijnctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.Disruption
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.lines.LineInfo
import com.vknxworks.lijnctrl.data.settings.TimeStyle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------- Time ----------

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

/** Belgian clock time, e.g. "14:32". De Lijn's times are Belgian, so they're shown that way wherever you are. */
fun clockTime(t: Instant): String = CLOCK.format(t.atZone(BRUSSELS))

fun clockTimeSeconds(t: Instant): String = CLOCK_SECONDS.format(t.atZone(BRUSSELS))

/** "3 Oct 14:00", or just "14:00" for today. */
fun dayTime(t: Instant): String {
    val z = t.atZone(BRUSSELS)
    return if (z.toLocalDate() == LocalDate.now(BRUSSELS)) CLOCK.format(z) else DAY_TIME.format(z)
}

fun day(t: Instant): String = DAY.format(t.atZone(BRUSSELS))

/** The current time, updated every [everyMs] while shown, so "3 min" counts down on its own. */
@Composable
fun rememberNow(everyMs: Long = 10_000L): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(everyMs)
        value = Instant.now()
    }
}

/** How a departure time is written: "Now", "4 min" or "14:32", depending on the setting. */
@Composable
fun departureLabel(d: Departure, now: Instant, style: TimeStyle): String {
    val minutes = Math.floorDiv(d.time.toEpochMilli() - now.toEpochMilli(), 60_000L)
    return when {
        style == TimeStyle.CLOCK -> clockTime(d.time)
        minutes <= 0 -> stringResource(R.string.now)
        minutes < 60 -> stringResource(R.string.x_min_short, minutes.toInt())
        else -> clockTime(d.time)
    }
}

// ---------- Line badge ----------

private fun hex(value: String?): Color? = value?.removePrefix("#")?.takeIf { it.length == 6 }?.let {
    runCatching { Color(0xFF000000 or it.toLong(16)) }.getOrNull()
}

/** The line's number in De Lijn's own colours (as on the bus and the stop sign). */
@Composable
fun LineBadge(info: LineInfo?, fallback: String?, modifier: Modifier = Modifier, minWidth: Dp = 44.dp, large: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val bg = hex(info?.background) ?: cs.surfaceContainerHighest
    val fg = hex(info?.foreground) ?: if (bg.luminance() > 0.5f) Color.Black else Color.White
    val border = hex(info?.backgroundBorder)?.takeIf { it != bg }
    val label = info?.publicNumber ?: fallback.orEmpty()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .widthIn(min = minWidth)
            .heightIn(min = if (large) 36.dp else 28.dp)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

/** Looks up a line's info from the cache, asking De Lijn for it once if it isn't known yet. */
@Composable
fun rememberLineInfo(lines: Collection<LineKey>): Map<String, LineInfo> {
    val repo = LocalContext.current.container.lines
    val known by repo.lines.collectAsStateWithLifecycle()
    val keys = remember(lines) { lines.toSet() }
    LaunchedEffect(keys) { if (keys.isNotEmpty()) repo.ensure(keys) }
    return known
}

// ---------- Departure row ----------

@Composable
fun DepartureRow(
    d: Departure,
    info: LineInfo?,
    now: Instant,
    style: TimeStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(info, d.line?.line)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.destination.ifBlank { stringResource(R.string.unknown_destination) },
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
                color = if (d.cancelled) cs.onSurfaceVariant else cs.onSurface,
            )
            if (!compact && d.vias.isNotEmpty()) {
                Text(
                    stringResource(R.string.via_x, d.vias.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        DepartureTime(d, now, style, compact)
    }
}

/** The time on the right: "4 min" with a live marker, how late it is, or "Cancelled". */
@Composable
fun DepartureTime(d: Departure, now: Instant, style: TimeStyle, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        if (d.cancelled) {
            Text(stringResource(R.string.cancelled), color = cs.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(clockTime(d.scheduled), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textDecoration = TextDecoration.LineThrough)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (d.live) {
                Icon(
                    Icons.Outlined.Sensors,
                    stringResource(R.string.live),
                    Modifier.size(14.dp),
                    tint = cs.primary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                departureLabel(d, now, style),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (d.live) cs.primary else cs.onSurface,
            )
        }
        val delay = d.delayMinutes
        val sub = when {
            delay == null -> stringResource(R.string.timetable)
            delay >= 1 -> stringResource(R.string.x_min_late, delay.toInt())
            delay <= -1 -> stringResource(R.string.x_min_early, (-delay).toInt())
            else -> stringResource(R.string.on_time)
        }
        val subColor = when {
            delay != null && delay >= 3 -> cs.error
            delay != null && delay >= 1 -> MaterialTheme.colorScheme.tertiary
            else -> cs.onSurfaceVariant
        }
        // With clock times the scheduled time is already shown, so only the delay is added.
        Text(
            if (style == TimeStyle.MINUTES && delay != null && delay != 0L) "$sub · ${clockTime(d.time)}" else sub,
            style = MaterialTheme.typography.bodySmall,
            color = subColor,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

// ---------- Disruptions ----------

@Composable
fun DisruptionCard(item: Disruption, lines: Map<String, LineInfo>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.errorContainer.copy(alpha = 0.35f),
        contentColor = cs.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = cs.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title.ifBlank { stringResource(R.string.disruption) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            if (item.lines.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.lines.take(8).forEach { k -> LineBadge(lines[k.toString()], k.line, minWidth = 32.dp) }
                }
            }
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium)
            val period = when {
                item.start != null && item.end != null -> stringResource(R.string.from_x_until_y, dayTime(item.start), dayTime(item.end))
                item.start != null -> stringResource(R.string.since_x, dayTime(item.start))
                item.end != null -> stringResource(R.string.until_x, dayTime(item.end))
                else -> null
            }
            if (period != null) Text(period, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** "Updated 14:03:21", ticking along with refreshes. */
@Composable
fun UpdatedLine(at: Instant?, modifier: Modifier = Modifier) {
    if (at == null) return
    Text(
        stringResource(R.string.updated_x, clockTimeSeconds(at)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
