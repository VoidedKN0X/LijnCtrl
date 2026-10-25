package com.vknxworks.lijnctrl.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import com.vknxworks.lijnctrl.ui.theme.CardBorder
import com.vknxworks.lijnctrl.ui.theme.ControlBorder
import com.vknxworks.lijnctrl.ui.theme.CtrlColors
import com.vknxworks.lijnctrl.ui.theme.CtrlShapes
import com.vknxworks.lijnctrl.ui.theme.CtrlType

// Ctrl / Signal building blocks shared by every screen.

/** "LIJN/CTRL": the app's wordmark, with the slash in the accent colour. */
@Composable
fun CtrlWordmark(modifier: Modifier = Modifier, name: String = "LIJN") {
    Text(
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.OnBackground,
        modifier = modifier,
        maxLines = 1,
    )
}

/** Mono, uppercase section label ("PRIVACY", "STOP 200553"). */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A 44dp square button (back, close, refresh): surface fill and a hairline border. */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.OnBackground,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Square)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Square)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
    }
}

/** Secondary card: surface, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke = CardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CtrlShapes.Card,
        color = CtrlColors.Surface,
        contentColor = CtrlColors.OnBackground,
        border = border,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(CtrlShapes.Card).clickable(onClick = onClick) else Modifier),
    ) {
        Column(content = content)
    }
}

/** The one most important thing on a screen: accent fill, dark content, 26dp corners. */
@Composable
fun CtrlHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CtrlShapes.Hero,
        color = CtrlColors.Accent,
        contentColor = CtrlColors.OnAccent,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(CtrlShapes.Hero).clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** An 8dp square status dot (2dp corners). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(color))
}

/**
 * The Ctrl switch: a 52x32 rounded rectangle with a 24dp square knob. Accent with a dark knob when on,
 * grey when off. Announced as a switch to accessibility services.
 */
@Composable
fun CtrlSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(shape)
            .background(if (checked) CtrlColors.Accent else CtrlColors.SwitchOffTrack)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onValueChange = onCheckedChange,
                    )
                } else Modifier,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) CtrlColors.OnAccent else CtrlColors.Inactive),
        )
    }
}

/** Main call to action: accent fill, dark text, 56dp tall. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ctrlPrimaryButtonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CtrlShapes.Button,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: surface fill and a hairline border. */
@Composable
fun CtrlSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CtrlShapes.Control,
        border = ControlBorder,
        colors = ctrlSecondaryButtonColors(),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ctrlPrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = CtrlColors.Accent,
    contentColor = CtrlColors.OnAccent,
    disabledContainerColor = CtrlColors.Raised,
    disabledContentColor = CtrlColors.Inactive,
)

@Composable
fun ctrlSecondaryButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = CtrlColors.Surface,
    contentColor = CtrlColors.OnBackground,
    disabledContainerColor = CtrlColors.Surface,
    disabledContentColor = CtrlColors.Inactive,
)

/** Text fields: surface fill, hairline border, accent border and cursor when focused. Use with [CtrlShapes.Control]. */
@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.Accent,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    disabledBorderColor = CtrlColors.Outline,
    cursorColor = CtrlColors.Accent,
    focusedLabelColor = CtrlColors.Accent,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.OnBackground,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedPlaceholderColor = CtrlColors.Muted,
    unfocusedPlaceholderColor = CtrlColors.Muted,
)

/** A filter chip: 10dp corners, accent when selected. */
@Composable
fun CtrlChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CtrlColors.Surface,
            labelColor = CtrlColors.OnBackground,
            selectedContainerColor = CtrlColors.Accent,
            selectedLabelColor = CtrlColors.OnAccent,
            selectedLeadingIconColor = CtrlColors.OnAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CtrlColors.ControlOutline,
            selectedBorderColor = CtrlColors.Accent,
        ),
    )
}

/**
 * A column that draws a 1dp divider between its children (skipping ones that take no space), like the rows
 * of a settings card.
 */
@Composable
fun DividedColumn(modifier: Modifier = Modifier, color: Color = CtrlColors.Outline, content: @Composable () -> Unit) {
    val lines = remember { mutableListOf<Int>() }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            val stroke = 1.dp.toPx()
            lines.forEach { y -> drawRect(color, topLeft = Offset(0f, y.toFloat()), size = androidx.compose.ui.geometry.Size(size.width, stroke)) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = placeables.maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            lines.clear()
            var y = 0
            var seen = false
            placeables.forEach { p ->
                if (p.height > 0) {
                    if (seen) lines.add(y)
                    seen = true
                }
                p.placeRelative(0, y)
                y += p.height
            }
        }
    }
}

/** A 1dp list divider. */
@Composable
fun CtrlDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CtrlColors.Divider))
}

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }
