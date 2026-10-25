package com.vknxworks.lijnctrl.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.plan.Journey
import com.vknxworks.lijnctrl.data.plan.JourneyLeg
import com.vknxworks.lijnctrl.data.plan.TransitousPlanner
import com.vknxworks.lijnctrl.ui.components.LineBadge
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.clockTime
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nav.TripRoute
import com.vknxworks.lijnctrl.ui.theme.Gutter

/** One route, leg by leg: walks, and each ride with where to get on and off. */
@Composable
fun JourneyScreen(index: Int) {
    val journey = PlanState.results?.getOrNull(index)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.route)) },
    ) { padding ->
        if (journey == null) {
            MessageView(Icons.Outlined.Directions, stringResource(R.string.route_gone), stringResource(R.string.route_gone_body), Modifier.padding(padding))
            return@Scaffold
        }
        JourneyDetail(journey, index, Modifier.padding(padding))
    }
}

@Composable
private fun JourneyDetail(j: Journey, index: Int, modifier: Modifier) {
    val nav = LocalNavigator.current
    val lines = rememberLineInfo(j.legs.mapNotNull { it.line })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { JourneyCard(j, onClick = null) }
        if (j.legs.any { it.path.size >= 2 }) {
            item { RouteMapPreview(j) { nav.navigate(com.vknxworks.lijnctrl.ui.nav.RouteMapRoute(index)) } }
        }
        itemsIndexed(j.legs) { _, leg ->
            if (leg.isWalk) WalkLeg(leg)
            else RideLeg(
                leg,
                badge = legBadgeInfo(leg, lines),
                onLine = leg.line?.let { k -> { nav.line(k.entity, k.line, leg.direction) } },
                onTrip = if (leg.line != null && leg.trip != null && leg.direction != null && leg.serviceDate != null) {
                    { nav.navigate(TripRoute(leg.line.entity, leg.line.line, leg.direction, leg.trip, leg.serviceDate.toString(), leg.fromStop?.number)) }
                } else null,
                onFrom = leg.fromStop?.let { k -> { nav.stop(k.entity, k.number, leg.fromName) } },
                onTo = leg.toStop?.let { k -> { nav.stop(k.entity, k.number, leg.toName) } },
            )
        }
        item {
            Text(
                stringResource(if (j.source == TransitousPlanner.SOURCE) R.string.route_source_transitous else R.string.route_source_builtin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WalkLeg(leg: JourneyLeg) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            listOfNotNull(
                stringResource(R.string.walk_x_min_to_y, leg.minutes.toInt(), leg.toName),
                leg.distanceMetres?.takeIf { it > 0 }?.let { stringResource(R.string.x_m_away, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RideLeg(
    leg: JourneyLeg,
    badge: com.vknxworks.lijnctrl.data.lines.LineInfo?,
    onLine: (() -> Unit)?,
    onTrip: (() -> Unit)?,
    onFrom: (() -> Unit)?,
    onTo: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (onLine != null) Modifier.clickable(onClick = onLine) else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(badge, leg.badge ?: "?", large = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        leg.headsign?.let { stringResource(R.string.to_x, it) } ?: leg.toName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (leg.cancelled) TextDecoration.LineThrough else null,
                    )
                    val sub = listOfNotNull(
                        leg.operator?.takeIf { !it.contains("Lijn", true) },
                        if (leg.cancelled) stringResource(R.string.cancelled) else null,
                        if (leg.live) stringResource(R.string.live) else null,
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (leg.cancelled) cs.error else cs.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
            StopLine(clockTime(leg.start), leg.scheduledStart?.takeIf { it != leg.start }?.let(::clockTime), stringResource(R.string.get_on_at_x, leg.fromName), leg.track?.let { stringResource(R.string.platform_x, it) }, onFrom)
            if (leg.stopsBetween > 0 || onTrip != null) {
                Row(
                    Modifier.fillMaxWidth().then(if (onTrip != null) Modifier.clickable(onClick = onTrip) else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(56.dp))
                    Text(
                        listOfNotNull(
                            pluralStringResource(R.plurals.stops_between, leg.stopsBetween + 1, leg.stopsBetween + 1),
                            stringResource(R.string.x_min_short, leg.minutes.toInt()),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (onTrip != null) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (onTrip != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.show_trip), Modifier.size(18.dp), tint = cs.primary)
                }
            }
            StopLine(clockTime(leg.end), leg.scheduledEnd?.takeIf { it != leg.end }?.let(::clockTime), stringResource(R.string.get_off_at_x, leg.toName), null, onTo)
        }
    }
}

@Composable
private fun StopLine(time: String, scheduled: String?, text: String, extra: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text(time, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (scheduled != null) {
                Text(scheduled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
