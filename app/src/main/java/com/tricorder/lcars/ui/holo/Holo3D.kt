package com.tricorder.lcars.ui.holo

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Minimal 3D point for wireframe holograms. */
data class P3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Float) = P3(x * s, y * s, z * s)
}

fun P3.rotX(a: Float): P3 {
    val c = cos(a); val s = sin(a)
    return P3(x, c * y - s * z, s * y + c * z)
}

fun P3.rotY(a: Float): P3 {
    val c = cos(a); val s = sin(a)
    return P3(c * x + s * z, y, -s * x + c * z)
}

fun P3.rotZ(a: Float): P3 {
    val c = cos(a); val s = sin(a)
    return P3(c * x - s * y, s * x + c * y, z)
}

/**
 * SensorManager matrices map device → world (world = R·device).
 * Rendering a world-fixed object in the phone's frame needs device = Rᵀ·world.
 */
fun P3.worldToDevice(r: FloatArray) = P3(
    r[0] * x + r[3] * y + r[6] * z,
    r[1] * x + r[4] * y + r[7] * z,
    r[2] * x + r[5] * y + r[8] * z,
)

/**
 * Perspective projector. Camera sits on +Z looking toward -Z
 * (device convention: X right, Y up the screen, Z out of the glass).
 */
class Projector(val center: Offset, val focal: Float, val distance: Float) {
    fun project(p: P3): Offset {
        val k = focal / (distance - p.z).coerceAtLeast(0.1f)
        return Offset(center.x + p.x * k, center.y - p.y * k)
    }

    /** Nearer = brighter. */
    fun depthAlpha(p: P3, range: Float = 1.3f): Float =
        (0.25f + 0.75f * ((p.z + range) / (2 * range))).coerceIn(0.12f, 1f)
}

/** Glowing hologram line: soft wide halo + crisp core, alpha by depth. */
fun DrawScope.holoLine(
    proj: Projector, a: P3, b: P3, color: Color, width: Float,
    glow: Boolean = true, alphaScale: Float = 1f,
) {
    val alpha = ((proj.depthAlpha(a) + proj.depthAlpha(b)) / 2f * alphaScale).coerceIn(0f, 1f)
    val pa = proj.project(a)
    val pb = proj.project(b)
    if (glow) {
        drawLine(color.copy(alpha = alpha * 0.22f), pa, pb, strokeWidth = width * 4.5f, cap = StrokeCap.Round)
    }
    drawLine(color.copy(alpha = alpha), pa, pb, strokeWidth = width, cap = StrokeCap.Round)
}

fun DrawScope.holoPolyline(
    proj: Projector, pts: List<P3>, color: Color, width: Float,
    closed: Boolean = false, glow: Boolean = true, alphaScale: Float = 1f,
) {
    for (i in 0 until pts.size - 1) holoLine(proj, pts[i], pts[i + 1], color, width, glow, alphaScale)
    if (closed && pts.size > 2) holoLine(proj, pts.last(), pts.first(), color, width, glow, alphaScale)
}

/** CRT-style scanlines plus a slow rolling brightness band. */
fun DrawScope.holoScanlines(color: Color, time: Float) {
    var y = 0f
    while (y < size.height) {
        drawLine(color.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 4f
    }
    val bandY = (time * 0.35f % 1f) * size.height
    drawLine(color.copy(alpha = 0.07f), Offset(0f, bandY), Offset(size.width, bandY), strokeWidth = size.height * 0.06f)
}

/** Corner brackets framing a hologram viewport. */
fun DrawScope.holoBrackets(color: Color) {
    val l = size.minDimension * 0.08f
    val w = 3f
    val c = color.copy(alpha = 0.8f)
    drawLine(c, Offset(0f, 0f), Offset(l, 0f), w); drawLine(c, Offset(0f, 0f), Offset(0f, l), w)
    drawLine(c, Offset(size.width, 0f), Offset(size.width - l, 0f), w); drawLine(c, Offset(size.width, 0f), Offset(size.width, l), w)
    drawLine(c, Offset(0f, size.height), Offset(l, size.height), w); drawLine(c, Offset(0f, size.height), Offset(0f, size.height - l), w)
    drawLine(c, Offset(size.width, size.height), Offset(size.width - l, size.height), w)
    drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - l), w)
}

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

fun DrawScope.holoText(
    text: String, pos: Offset, color: Color, sizePx: Float,
    align: Paint.Align = Paint.Align.CENTER,
) {
    drawIntoCanvas { canvas ->
        textPaint.color = color.toArgb()
        textPaint.textSize = sizePx
        textPaint.textAlign = align
        canvas.nativeCanvas.drawText(text, pos.x, pos.y + sizePx * 0.35f, textPaint)
    }
}

/** Seconds since first composition, updated every frame. Read it inside draw lambdas. */
@Composable
fun rememberHoloTime(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return t
}

fun lerpColor(a: Color, b: Color, f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * x,
        green = a.green + (b.green - a.green) * x,
        blue = a.blue + (b.blue - a.blue) * x,
        alpha = a.alpha + (b.alpha - a.alpha) * x,
    )
}

/**
 * Smoothly integrated phase: advances by speed × frame time, with the speed itself
 * eased toward its target. Changing speed never makes the animation jump.
 */
@Composable
fun rememberPhase(speed: Float, smoothing: Float = 3f): State<Float> {
    val target by rememberUpdatedState(speed)
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var cur = target
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                cur += (target - cur) * (1f - exp(-smoothing * dt))
                phase.floatValue += cur * dt
            }
        }
    }
    return phase
}

/**
 * Interactive hologram surface: tap = expanding ripple + [onTap] callback,
 * horizontal drag = manual yaw (passed to [onDraw]); vertical drags still scroll the page.
 */
@Composable
fun HoloCanvas(
    modifier: Modifier,
    color: Color,
    onTap: () -> Unit,
    onDraw: DrawScope.(t: Float, dragYaw: Float) -> Unit,
) {
    val time = rememberHoloTime()
    val yaw = remember { mutableFloatStateOf(0f) }
    val ripples = remember { mutableStateListOf<Pair<Offset, Float>>() }
    val tap by rememberUpdatedState(onTap)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    ripples.add(pos to time.value)
                    if (ripples.size > 6) ripples.removeAt(0)
                    tap()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    yaw.floatValue += dx * 0.008f
                }
            }
    ) {
        val t = time.value
        onDraw(t, yaw.floatValue)
        for ((pos, start) in ripples) {
            val age = t - start
            if (age in 0f..1.2f) {
                val f = age / 1.2f
                val r = size.minDimension * 0.55f * f
                drawCircle(color.copy(alpha = (1f - f) * 0.9f), r, pos, style = Stroke(3f))
                drawCircle(color.copy(alpha = (1f - f) * 0.4f), r * 0.6f, pos, style = Stroke(2f))
            }
        }
    }
}
