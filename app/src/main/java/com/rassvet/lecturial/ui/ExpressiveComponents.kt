package com.rassvet.lecturial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
internal fun BackIconButton(onBack: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Назад") {
    // Pull the 48dp touch target left so the arrow lines up with the screen's text edge.
    IconButton(onClick = onBack, modifier = modifier.offset(x = (-12).dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = contentDescription)
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

/** Shapes of button [index] out of [count] in an Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun connectedButtonShapes(index: Int, count: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
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

/**
 * One block of a settings page on its own tonal surface. Material 3 Expressive groups
 * controls on containers rather than laying them on the background between dividers, and
 * on line and colour wallpapers the text stays readable.
 */
@Composable
internal fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null || description != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
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

/**
 * A settings sub-screen: a Material 3 top app bar with the back arrow, whose large title
 * collapses into the bar as the content scrolls. The bar stays transparent so the app
 * background runs on up to the status bar; scrolled content fades out under it instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scroll = rememberScrollState()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        // The status bar is already padded above and the navigation bar sits below.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
                // A filled bar on scroll began only below the status bar, a solid band
                // with the wallpaper above it and the content cut off sharply beneath.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingTopEdge { scroll.value.toFloat() }
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            overlay()
        }
    }
}

/**
 * Fades the content out towards the top edge, by up to [ScrollFade] once [scrolled] pixels
 * have gone under it; at the top of the page nothing fades. Read during drawing, so
 * scrolling redraws only this layer.
 */
private fun Modifier.fadingTopEdge(scrolled: () -> Float): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = scrolled().coerceAtMost(ScrollFade.toPx())
            if (fade > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

private val ScrollFade = 28.dp
