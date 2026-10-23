package com.tricorder.lcars.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType

/**
 * The signature LCARS "elbow": a vertical sidebar joined to a horizontal bar with
 * a large rounded outer corner and a small concave inner corner.
 */
class ElbowShape(
    private val sidebarWidth: Dp,
    private val barHeight: Dp,
    private val outerRadius: Dp,
    private val innerRadius: Dp,
    private val bottom: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val w = size.width
            val h = size.height
            val sw = sidebarWidth.toPx()
            val bh = barHeight.toPx()
            val r = innerRadius.toPx()
            val big = outerRadius.toPx().coerceAtMost(minOf(sw, h))
            val p = Path()
            if (!bottom) {
                p.moveTo(big, 0f)
                p.lineTo(w, 0f)
                p.lineTo(w, bh)
                p.lineTo(sw + r, bh)
                p.arcTo(Rect(sw, bh, sw + 2 * r, bh + 2 * r), 270f, -90f, false)
                p.lineTo(sw, h)
                p.lineTo(0f, h)
                p.lineTo(0f, big)
                p.arcTo(Rect(0f, 0f, 2 * big, 2 * big), 180f, 90f, false)
            } else {
                p.moveTo(0f, 0f)
                p.lineTo(sw, 0f)
                p.lineTo(sw, h - bh - r)
                p.arcTo(Rect(sw, h - bh - 2 * r, sw + 2 * r, h - bh), 180f, -90f, false)
                p.lineTo(w, h - bh)
                p.lineTo(w, h)
                p.lineTo(big, h)
                p.arcTo(Rect(0f, h - 2 * big, 2 * big, h), 90f, 90f, false)
            }
            p.close()
            return Outline.Generic(p)
        }
    }
}

@Composable
fun Elbow(
    color: Color,
    sidebarWidth: Dp,
    barHeight: Dp,
    bottom: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(
        modifier = modifier.background(
            color,
            ElbowShape(sidebarWidth, barHeight, outerRadius = 56.dp, innerRadius = 22.dp, bottom = bottom),
        ),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = LcarsType.Button,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(if (bottom) Alignment.TopStart else Alignment.BottomStart)
                    .width(sidebarWidth)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Horizontal bar segment; set capEnd for the rounded "pill" terminator. */
@Composable
fun BarSegment(color: Color, modifier: Modifier = Modifier, capStart: Boolean = false, capEnd: Boolean = false) {
    val shape = RoundedCornerShape(
        topStartPercent = if (capStart) 50 else 0,
        bottomStartPercent = if (capStart) 50 else 0,
        topEndPercent = if (capEnd) 50 else 0,
        bottomEndPercent = if (capEnd) 50 else 0,
    )
    Box(modifier.background(color, shape))
}

/** Rectangular sidebar button with LCARS bottom-right label placement. */
@Composable
fun SidebarButton(
    text: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "sel")
    val a by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "selA",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) LcarsColors.Text.copy(alpha = a) else color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(text, style = LcarsType.Button, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
    }
}

/** Pill-shaped section header: rounded start cap, label, flowing bar. */
@Composable
fun SectionHeader(title: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarSegment(color, Modifier.width(18.dp).height(24.dp), capStart = true)
        Text(
            title,
            style = LcarsType.Label.copy(color = color),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        BarSegment(color, Modifier.weight(1f).height(24.dp), capEnd = true)
    }
}

/** Label on the left, monospaced value on the right. Null value renders as OFFLINE. */
@Composable
fun Readout(label: String, value: String?, unit: String = "", valueColor: Color = LcarsColors.Text) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = LcarsType.Label)
        Text(
            text = if (value == null) "OFFLINE" else if (unit.isEmpty()) value else "$value $unit",
            style = LcarsType.Readout.copy(color = if (value == null) LcarsColors.Red else valueColor),
        )
    }
}

/** Segmented LCARS gauge; fraction is clamped to 0..1. */
@Composable
fun SegmentGauge(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 20) {
    val lit = (fraction.coerceIn(0f, 1f) * segments).toInt()
    Row(modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .background(if (i < lit) color else LcarsColors.Dim.copy(alpha = 0.4f)),
            )
        }
    }
}

/** Animated scan line sweeping across a thin track — the "active scan" indicator. */
@Composable
fun ScanSweep(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sweep")
    val x by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sweepX",
    )
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(color.copy(alpha = 0.18f))
        val bw = size.width * 0.18f
        val left = (size.width + bw) * x - bw
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(left, 0f), size = Size(bw, size.height))
    }
}

/** Blinking status lamp with a label, e.g. "SCANNING". */
@Composable
fun StatusLamp(text: String, color: Color) {
    val t = rememberInfiniteTransition(label = "lamp")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "lampA",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).alpha(a).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = LcarsType.Small.copy(color = color))
    }
}

fun Float.fmt(decimals: Int): String = "%.${decimals}f".format(this)

/** Signed fixed-width format so readouts don't jitter horizontally. */
fun Float.sfmt(decimals: Int, width: Int = 8): String = "%+${width}.${decimals}f".format(this)
