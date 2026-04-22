package com.racketmatch.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit

/* ══════════════════════════════════════════════════════════════════════════
   Design-system primitives — ported from the Claude Design redesign.
   ══════════════════════════════════════════════════════════════════════════ */

// ─── Typography ───────────────────────────────────────────────────────────

/** UPPERCASE micro-label above page titles (e.g. "Sobota · 19 kwietnia"). */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ProCircuit.Ink2,
) = Text(
    text = text.uppercase(),
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.54.sp,
    color = color,
)

/** Even smaller uppercase label — used inside cards/stat-tiles. */
@Composable
fun Tiny(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ProCircuit.Ink2,
) = Text(
    text = text.uppercase(),
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp,
    color = color,
)

@Composable
fun H1(text: String, modifier: Modifier = Modifier, color: Color = ProCircuit.Ink) = Text(
    text = text,
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 36.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1.2).sp,
    color = color,
)

@Composable
fun H2(text: String, modifier: Modifier = Modifier, color: Color = ProCircuit.Ink) = Text(
    text = text,
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.6).sp,
    color = color,
)

@Composable
fun H3(text: String, modifier: Modifier = Modifier, color: Color = ProCircuit.Ink) = Text(
    text = text,
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    color = color,
)

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ProCircuit.Ink2,
) = Text(
    text = text,
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = color,
)

@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ProCircuit.Ink2,
) = Text(
    text = text,
    modifier = modifier,
    fontFamily = AppFontFamily,
    fontSize = 12.sp,
    color = color,
)

/** Monospace text — used across the design for every number (ELO, scores, ranks, dates). */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = ProCircuit.Ink,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
) = Text(
    text = text,
    modifier = modifier,
    fontFamily = FontFamily.Monospace,
    fontSize = fontSize,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing,
    color = color,
)

// ─── Cards ────────────────────────────────────────────────────────────────

val CardShape: Shape = RoundedCornerShape(22.dp)
val CardTightShape: Shape = RoundedCornerShape(18.dp)
val HeroShape: Shape = RoundedCornerShape(28.dp)

/** Flat white card, 22dp radius, 18dp padding. */
@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    padding: Dp = 18.dp,
    background: Color = ProCircuit.SurfaceLow,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = modifier
        .clip(shape)
        .background(background)
        .padding(padding),
    content = content,
)

/**
 * Dark forest-filled hero card (e.g. Today next-match, Profile hero).
 * Renders an optional huge watermark number in the top-right corner at 7% opacity.
 */
@Composable
fun DarkHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = HeroShape,
    padding: Dp = 22.dp,
    watermark: String? = null,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = modifier
        .clip(shape)
        .background(ProCircuit.Forest2)
        .padding(padding),
) {
    if (watermark != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp, top = 0.dp),
        ) {
            Text(
                text = watermark,
                fontFamily = AppFontFamily,
                fontSize = 180.sp,
                lineHeight = 180.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-10).sp,
                color = Color.White.copy(alpha = 0.07f),
            )
        }
    }
    content()
}

/** Small KPI tile used in 3- or 4-column grids. */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) = DsCard(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    padding = padding,
    content = content,
)

// ─── Avatars ──────────────────────────────────────────────────────────────

enum class AvaTone { Lime, Blue, Forest }

@Composable
fun Ava(
    initials: String,
    size: Dp = 44.dp,
    tone: AvaTone = AvaTone.Lime,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (tone) {
        AvaTone.Lime -> ProCircuit.Lime to ProCircuit.LimeInk
        AvaTone.Blue -> ProCircuit.Blue to Color.White
        AvaTone.Forest -> ProCircuit.Forest to ProCircuit.ForestInk
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontFamily = AppFontFamily,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = fg,
        )
    }
}

// ─── Buttons ──────────────────────────────────────────────────────────────

@Composable
fun ButtonLime(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 22.dp,
    verticalPadding: Dp = 14.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) = Box(
    modifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .background(ProCircuit.Lime)
        .clickable(onClick = onClick)
        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    contentAlignment = Alignment.Center,
) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = ProCircuit.LimeInk,
    )
}

@Composable
fun ButtonForest(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 12.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) = Box(
    modifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .background(ProCircuit.Forest)
        .clickable(onClick = onClick)
        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    contentAlignment = Alignment.Center,
) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = ProCircuit.ForestInk,
    )
}

@Composable
fun ButtonGhost(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 12.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) = Box(
    modifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .border(1.5.dp, ProCircuit.Stroke2, RoundedCornerShape(999.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    contentAlignment = Alignment.Center,
) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = ProCircuit.Ink,
    )
}

/** Circular 42dp icon button with subtle surface. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    background: Color = ProCircuit.SurfaceLow,
    tint: Color = ProCircuit.Ink,
) = Box(
    modifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(background)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, contentDescription = contentDescription, tint = tint)
}

/** Floating action button — lime disc with glow shadow. */
@Composable
fun LimeFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Send,
    contentDescription: String? = null,
) = Box(
    modifier = modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(ProCircuit.Lime)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, contentDescription = contentDescription, tint = ProCircuit.LimeInk)
}

// ─── Small composites ─────────────────────────────────────────────────────

/** Pill-shaped status badge (e.g. "Zaakceptowane"). */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = ProCircuit.Ink,
    contentColor: Color = Color.White,
) = Box(
    modifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .background(background)
        .padding(horizontal = 10.dp, vertical = 4.dp),
) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
    )
}

/** Rounded square icon container used as list-row leading (38dp / 10dp radius). */
@Composable
fun IconSquare(
    icon: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    background: Color = ProCircuit.Lime,
    contentColor: Color = ProCircuit.LimeInk,
    size: Dp = 38.dp,
) = Box(
    modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(10.dp))
        .background(background),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, contentDescription = contentDescription, tint = contentColor)
}

/** 5-pip streak bar — lit pips in lime, unlit in stroke color. */
@Composable
fun StreakPips(
    litCount: Int,
    totalCount: Int = 5,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    gap: Dp = 3.dp,
) = Row(
    modifier = modifier.fillMaxWidth().height(height),
    horizontalArrangement = Arrangement.spacedBy(gap),
) {
    repeat(totalCount) { i ->
        Box(
            modifier = Modifier
                .weight(1f)
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (i < litCount) ProCircuit.Lime else ProCircuit.Stroke),
        )
    }
}

/** H2H (head-to-head) progress bar. */
@Composable
fun H2HBar(
    wins: Int,
    total: Int,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) = Box(
    modifier = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(2.dp))
        .background(ProCircuit.Bg2),
) {
    val fraction = if (total <= 0) 0f else (wins.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(ProCircuit.Forest),
    )
}

/** Segmented control with sliding selection pill. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ProCircuit.Bg2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, (value, label) ->
            val active = i == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) ProCircuit.SurfaceLow else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) ProCircuit.Ink else ProCircuit.Ink2,
                )
            }
        }
    }
}

/**
 * Area sparkline with line stroke + gradient fill.
 * Uses Canvas; no external deps. Pass evenly-spaced Y values.
 *
 * When [animate] is true, reveals left-to-right on first composition
 * (matching the design's stroke-dash animation).
 */
@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ProCircuit.Lime,
    animate: Boolean = true,
) {
    if (data.size < 2) return

    var started by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f),
        ),
        label = "sparkline-reveal",
    )

    Box(modifier = modifier.drawBehind {
        val w = size.width
        val h = size.height
        val pad = 4f
        val min = data.min()
        val max = data.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val pts = data.mapIndexed { i, v ->
            val x = i / (data.size - 1).toFloat() * w
            val y = h - pad - (v - min) / range * (h - pad * 3)
            x to y
        }
        val line = Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        // Clip to the animated progress so the chart draws left-to-right.
        clipRect(left = 0f, top = 0f, right = w * progress, bottom = h) {
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    0f to lineColor.copy(alpha = 0.5f),
                    1f to lineColor.copy(alpha = 0f),
                ),
                style = Fill,
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = 2.4f * density),
            )
        }
    })
}

// ─── Row helpers ──────────────────────────────────────────────────────────

/**
 * Generic list-row inside a tight card: leading slot, title + subtitle,
 * trailing slot. Used by invites, rivals, notifications, etc.
 */
@Composable
fun DsListRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) = DsCard(modifier = modifier, shape = CardTightShape, padding = 14.dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProCircuit.Ink,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    color = ProCircuit.Ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

/** Same as DsListRow but transparent — used when the row lives inside another card. */
@Composable
fun InlineRow(
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    center: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    if (leading != null) leading()
    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
        center()
    }
    trailing()
}
