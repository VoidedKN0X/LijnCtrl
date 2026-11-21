package com.vknxworks.lijnctrl.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.live.RtAlert
import com.vknxworks.lijnctrl.data.live.RtText
import com.vknxworks.lijnctrl.data.settings.MessageLanguage
import com.vknxworks.lijnctrl.ui.theme.CtrlColors
import com.vknxworks.lijnctrl.ui.theme.CtrlShapes

/** How full a vehicle is, from the GTFS Realtime OccupancyStatus number; null when unknown or not given. */
@Composable
fun occupancyLabel(status: Int?): String? = when (status) {
    0 -> stringResource(R.string.occupancy_empty)
    1 -> stringResource(R.string.occupancy_many_seats)
    2 -> stringResource(R.string.occupancy_few_seats)
    3 -> stringResource(R.string.occupancy_standing)
    4 -> stringResource(R.string.occupancy_crushed)
    5 -> stringResource(R.string.occupancy_full)
    6, 8 -> stringResource(R.string.occupancy_not_boarding)
    else -> null
}

/** The text in the chosen message language, else Dutch, else the first one given. */
fun pickText(texts: List<RtText>, language: MessageLanguage): String? {
    val want = when (language) {
        MessageLanguage.ENGLISH -> "en"
        MessageLanguage.DUTCH -> "nl"
        MessageLanguage.FRENCH -> "fr"
        MessageLanguage.GERMAN -> "de"
    }
    fun of(code: String) = texts.firstOrNull { it.language?.lowercase()?.startsWith(code) == true }
    return (of(want) ?: of("nl") ?: texts.firstOrNull())?.text
}

/** A service alert from De Lijn's live feed: its title, tap for the full text. */
@Composable
fun LiveAlertCard(alert: RtAlert, language: MessageLanguage, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val header = pickText(alert.header, language)
    val body = pickText(alert.description, language)?.takeIf { it != header }
    var expanded by rememberSaveable(alert.id, header) { mutableStateOf(false) }
    Surface(
        modifier.fillMaxWidth(),
        shape = CtrlShapes.Card,
        color = CtrlColors.Surface,
        contentColor = cs.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.error.copy(alpha = 0.45f)),
    ) {
        Column(
            Modifier.then(if (body != null) Modifier.clickable { expanded = !expanded } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Campaign, null, Modifier.size(20.dp), tint = cs.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    header ?: body.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                )
            }
            if (expanded && body != null && header != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text(stringResource(R.string.live_alert_source), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
