package com.rassvet.lecturial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Outer corners of a settings group; items inside it meet with [SettingsGroupInnerRadius]. */
private val SettingsGroupOuterRadius = 24.dp
private val SettingsGroupInnerRadius = 6.dp
internal val SettingsGroupGap = 3.dp

/**
 * Material 3 Expressive list grouping: neighbouring items share small inner corners and
 * keep the large radius only on the outside edges of the group.
 */
internal fun settingsGroupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) SettingsGroupOuterRadius else SettingsGroupInnerRadius
    val bottom = if (index == count - 1) SettingsGroupOuterRadius else SettingsGroupInnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
internal fun BackIconButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Pull the 48dp touch target left so the arrow lines up with the screen's text edge.
    IconButton(onClick = onBack, modifier = modifier.offset(x = (-12).dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
    }
}

@Composable
internal fun SettingsChevron(tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = tint,
    )
}

/** Tonal round badge that leads a settings list item. */
@Composable
internal fun SettingsLeadingIcon(icon: ImageVector, emphasized: Boolean = false) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
    }
}

/** Groups settings items with Expressive segmented corners. */
@Composable
internal fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: SettingsGroupScope.() -> Unit,
) {
    val scope = SettingsGroupScope().apply(content)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SettingsGroupGap)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        scope.items.forEachIndexed { index, item -> item(settingsGroupShape(index, scope.items.size)) }
    }
}

internal class SettingsGroupScope {
    val items = mutableListOf<@Composable (RoundedCornerShape) -> Unit>()
    fun item(content: @Composable (shape: RoundedCornerShape) -> Unit) {
        items += content
    }
}

/** One row of a [SettingsGroup]: icon, two lines of text and a trailing slot. */
@Composable
internal fun SettingsListItem(
    title: String,
    subtitle: String?,
    shape: RoundedCornerShape,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    emphasizedIcon: Boolean = false,
    trailing: @Composable () -> Unit = { if (onClick != null) SettingsChevron() },
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsLeadingIcon(icon, emphasizedIcon)
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
    val semanticsModifier = if (accessibilityLabel != null) {
        modifier.semantics { contentDescription = accessibilityLabel }
    } else {
        modifier
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = semanticsModifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = rowContent,
        )
    } else {
        Surface(
            modifier = semanticsModifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = rowContent,
        )
    }
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.roundedBar(left: Float, top: Float, right: Float, bottom: Float) {
    val r = (right - left) / 2f
    moveTo(left, bottom)
    lineTo(left, top + r)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + r)
    lineTo(right, bottom)
    close()
}

private fun statisticsIcon(name: String, filled: Boolean): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        val bars: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit = {
            roundedBar(4.5f, 10f, 8.5f, 20f)
            roundedBar(10f, 4f, 14f, 20f)
            roundedBar(15.5f, 13f, 19.5f, 20f)
        }
        if (filled) {
            addPath(
                pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply(bars).nodes,
                fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
            )
        } else {
            addPath(
                pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply(bars).nodes,
                stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
    }.build()

/** Bar-chart icon for the statistics destination; core icons have no chart. */
internal val StatisticsIconFilled: ImageVector by lazy { statisticsIcon("StatisticsFilled", filled = true) }
internal val StatisticsIconOutlined: ImageVector by lazy { statisticsIcon("StatisticsOutlined", filled = false) }
