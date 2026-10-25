package com.vknxworks.lijnctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.vknxworks.lijnctrl.ui.theme.CtrlColors
import com.vknxworks.lijnctrl.ui.theme.CtrlDialogModifier
import com.vknxworks.lijnctrl.ui.theme.CtrlShapes
import com.vknxworks.lijnctrl.ui.theme.CtrlType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nav.YouRoute
import com.vknxworks.lijnctrl.ui.theme.Gutter
import androidx.compose.ui.res.stringResource
import com.vknxworks.lijnctrl.R

// ---------- Top bars ----------

/** Top bar for pushed screens: a square back button and the title in Space Grotesk. No colour fill. */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(CtrlColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 68.dp)
            .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = nav::back)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Header of the main tabs: the LIJN/CTRL wordmark and your profile picture on top, then the tab's name as a
 * big headline. Tapping the picture opens the You page with settings and about.
 */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val side = if (padded) Gutter else 0.dp
    Column(modifier.fillMaxWidth().padding(start = side, end = side, top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlWordmark(Modifier.weight(1f))
            trailing()
            if (showProfile) {
                Spacer(Modifier.width(8.dp))
                ProfileButton()
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The profile picture at the top right: a 44dp square with a small accent badge while LijnCtrl is
 * connected (your De Lijn key is set). Tapping it opens the You page with settings and about.
 */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Square)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Square)
                .clickable(role = Role.Button) { nav.navigate(YouRoute) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, stringResource(R.string.you), Modifier.size(22.dp), tint = CtrlColors.OnBackground)
        }
        // Connected: the main screens only show once a De Lijn key is saved.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 1.dp, bottom = 1.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CtrlColors.Accent)
                .border(2.dp, CtrlColors.Background, RoundedCornerShape(3.dp)),
        )
    }
}

/** Round-cornered square holding an icon, as used on empty states, setup and the You page. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 72.dp, accent: Boolean = false) {
    val shape = RoundedCornerShape(size / 3)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (accent) CtrlColors.Accent else CtrlColors.Surface)
            .then(if (accent) Modifier else Modifier.border(1.dp, CtrlColors.Outline, shape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.42f), tint = if (accent) CtrlColors.OnAccent else CtrlColors.Muted)
    }
}

// ---------- Grouped lists (settings, profile, admin) ----------

/** A card of related rows with 1dp dividers between them, under a mono section label. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = if (padded) Gutter else 0.dp, vertical = 10.dp)) {
        if (title != null) {
            CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        CtrlCard {
            DividedColumn(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Inside a [Group], rows are separated by hairlines.
    Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (showChevron) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
}

@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle, icon, onClick = { onChange(!checked) }) {
        // The row handles the tap; the switch itself is announced as a switch with its state.
        CtrlSwitch(checked, onCheckedChange = onChange)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    fallbackLabel: String = "",
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: fallbackLabel
    GroupRow(title, icon = icon, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CtrlColors.Surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        modifier = Modifier.border(1.dp, CtrlColors.Outline, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
    ) {
        CtrlLabel(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Buttons ----------

/** The one main action on a screen: accent fill, 56dp tall. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = CtrlColors.Accent,
    content: Color = CtrlColors.OnAccent,
) {
    CtrlButton(
        text,
        onClick,
        modifier,
        icon = icon,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
    )
}

/** Square secondary action with a short label underneath. */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(min = 64.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CtrlShapes.Control)
                .background(if (active) CtrlColors.Accent else CtrlColors.Surface)
                .border(1.dp, if (active) CtrlColors.Accent else CtrlColors.ControlOutline, CtrlShapes.Control),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(22.dp), tint = if (active) CtrlColors.OnAccent else CtrlColors.OnBackground)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------- Text helpers ----------

/** Facts about an item laid out with space between them instead of separator characters. */
@Composable
fun MetaRow(parts: List<String>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val visible = parts.filter { it.isNotBlank() }
    if (visible.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEach { Text(it, style = CtrlType.Data, color = color) }
    }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = CtrlDialogModifier,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
