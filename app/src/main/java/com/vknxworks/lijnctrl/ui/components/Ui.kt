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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** Plain top bar for pushed screens: back arrow and a title. */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (showBack) IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Big left-aligned title for the main tabs, with your profile picture at the end. */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = if (padded) Gutter else 0.dp, end = if (padded) 8.dp else 0.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        trailing()
        if (showProfile) ProfileButton()
    }
}

/** The profile picture at the top right; tapping it opens the You page with settings and about. */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    IconButton(onClick = { nav.navigate(YouRoute) }) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, stringResource(R.string.you), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- Grouped lists (settings, profile, admin) ----------

/** A rounded block of related rows, with an optional heading above it. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = if (padded) Gutter else 0.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
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
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = iconTint) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle, icon, onClick = { onChange(!checked) }) {
        Switch(checked, onCheckedChange = onChange)
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
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Filled.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Buttons ----------

/** The one main action on a screen. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Circular secondary action with a short label underneath. */
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
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
        visible.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = color) }
    }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
