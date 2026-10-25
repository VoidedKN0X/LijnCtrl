package com.vknxworks.lijnctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import com.vknxworks.lijnctrl.ui.theme.CtrlColors
import com.vknxworks.lijnctrl.ui.theme.CtrlShapes
import com.vknxworks.lijnctrl.ui.theme.CtrlType
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
    val shape = RoundedCornerShape(if (large) 10.dp else 9.dp)
    Box(
        modifier
            .widthIn(min = minWidth)
            .heightIn(min = if (large) 32.dp else 28.dp)
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
            style = if (large) CtrlType.Readout.copy(fontSize = 16.sp, lineHeight = 20.sp) else CtrlType.Data.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
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
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = if (compact) 52.dp else 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(info, d.line?.line, minWidth = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.destination.ifBlank { stringResource(R.string.unknown_destination) },
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
                color = if (d.cancelled) cs.onSurfaceVariant else cs.onSurface,
            )
            if (!compact && d.vias.isNotEmpty()) {
                Text(
                    stringResource(R.string.via_x, d.vias.joinToString(", ")),
                    style = CtrlType.DataSmall,
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

/** The time on the right: "4 min" in mono (accent when live, with a square dot), how late it is, or "Cancelled". */
@Composable
fun DepartureTime(d: Departure, now: Instant, style: TimeStyle, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        if (d.cancelled) {
            Text(stringResource(R.string.cancelled).uppercase(), color = cs.error, style = CtrlType.DataSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(clockTime(d.scheduled), color = cs.onSurfaceVariant, style = CtrlType.Data, textDecoration = TextDecoration.LineThrough)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (d.live) {
                val liveLabel = stringResource(R.string.live)
                StatusDot(CtrlColors.Accent, Modifier.semantics { contentDescription = liveLabel })
                Spacer(Modifier.width(6.dp))
            }
            Text(
                departureLabel(d, now, style),
                style = if (compact) CtrlType.Readout.copy(fontSize = 17.sp) else CtrlType.Readout,
                color = if (d.live) CtrlColors.Accent else cs.onSurface,
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
            delay == null -> CtrlColors.Inactive
            else -> cs.onSurfaceVariant
        }
        // With clock times the scheduled time is already shown, so only the delay is added.
        Text(
            (if (style == TimeStyle.MINUTES && delay != null && delay != 0L) "$sub · ${clockTime(d.time)}" else sub).uppercase(),
            style = CtrlType.DataSmall,
            color = subColor,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * The next bus or tram, as the Ctrl hero card: line and destination with a LIVE marker on top, the countdown
 * in giant mono numbers, the clock time and delay beside it, and a little progress track underneath.
 */
@Composable
fun NextDepartureHero(
    d: Departure,
    info: LineInfo?,
    now: Instant,
    style: TimeStyle,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val minutes = Math.floorDiv(d.time.toEpochMilli() - now.toEpochMilli(), 60_000L)
    // Accent: the hero of the screen. Otherwise the same layout on a surface card, with accent details.
    val ink = if (accent) CtrlColors.OnAccent else CtrlColors.OnBackground
    val inkSoft = if (accent) CtrlColors.OnAccent.copy(alpha = 0.62f) else CtrlColors.Muted
    val mark = if (accent) CtrlColors.OnAccent else CtrlColors.Accent
    val badgeText = if (accent) CtrlColors.Accent else CtrlColors.OnAccent
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .widthIn(min = 44.dp)
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(mark)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(info?.publicNumber ?: d.line?.line.orEmpty(), style = CtrlType.Readout.copy(fontSize = 16.sp, lineHeight = 20.sp), color = badgeText, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                listOfNotNull(
                    info?.let { stringResource(if (it.isTram) R.string.tram else R.string.bus) },
                    d.destination.ifBlank { stringResource(R.string.unknown_destination) },
                ).joinToString(" → "),
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                (if (d.live) "● " + stringResource(R.string.live) else stringResource(R.string.timetable)).uppercase(),
                style = CtrlType.DataSmall.copy(letterSpacing = 0.12.em),
                color = if (d.live) mark else inkSoft,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val big: String
            val unit: String?
            when {
                d.cancelled -> { big = stringResource(R.string.cancelled); unit = null }
                style == TimeStyle.CLOCK || minutes >= 60 -> { big = clockTime(d.time); unit = null }
                minutes <= 0 -> { big = stringResource(R.string.now); unit = null }
                else -> { big = minutes.toString().padStart(2, '0'); unit = stringResource(R.string.min_unit) }
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    big,
                    style = if (big.length > 5) CtrlType.Countdown.copy(fontSize = 40.sp, lineHeight = 44.sp) else CtrlType.Countdown,
                    color = ink,
                    maxLines = 1,
                )
                if (unit != null) {
                    Text(" $unit", style = CtrlType.Readout.copy(fontSize = 20.sp, letterSpacing = 0.sp), color = inkSoft, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            val delay = d.delayMinutes
            Column(horizontalAlignment = Alignment.End) {
                Text(clockTime(d.time), style = CtrlType.Data, color = inkSoft)
                Text(
                    when {
                        d.cancelled -> stringResource(R.string.cancelled)
                        delay != null && delay > 0 -> stringResource(R.string.x_min_late, delay.toInt())
                        delay != null && delay < 0 -> stringResource(R.string.x_min_early, (-delay).toInt())
                        d.live -> stringResource(R.string.on_time)
                        else -> stringResource(R.string.timetable)
                    },
                    style = CtrlType.Data,
                    color = inkSoft,
                )
            }
        }
        // Stop, ride, destination: the first part filled while it's on its way.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(mark))
            Box(Modifier.weight(1f).height(2.dp).background(mark))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, mark, RoundedCornerShape(3.dp)))
            Box(Modifier.weight(2f).height(2.dp).background(if (accent) inkSoft.copy(alpha = 0.35f) else CtrlColors.ProgressTrack))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, if (accent) inkSoft else CtrlColors.PinEmpty, RoundedCornerShape(3.dp)))
        }
    }
    if (accent) {
        CtrlHeroCard(modifier, onClick = onClick, content = body)
    } else {
        androidx.compose.material3.Surface(
            shape = CtrlShapes.Hero,
            color = CtrlColors.Surface,
            contentColor = CtrlColors.OnBackground,
            border = com.vknxworks.lijnctrl.ui.theme.CardBorder,
            modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(CtrlShapes.Hero).clickable(onClick = onClick) else Modifier),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
        }
    }
}

// ---------- Disruptions ----------

@Composable
fun DisruptionCard(item: Disruption, lines: Map<String, LineInfo>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = CtrlShapes.Card,
        color = CtrlColors.Surface,
        contentColor = cs.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.error.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = cs.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title.ifBlank { stringResource(R.string.disruption) },
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
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
            if (period != null) Text(period, style = CtrlType.Data, color = cs.onSurfaceVariant)
        }
    }
}

/** "Updated 14:03:21", ticking along with refreshes. */
@Composable
fun UpdatedLine(at: Instant?, modifier: Modifier = Modifier) {
    if (at == null) return
    Text(
        stringResource(R.string.updated_x, clockTimeSeconds(at)).uppercase(),
        style = CtrlType.DataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
