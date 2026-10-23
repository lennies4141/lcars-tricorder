package com.tricorder.lcars.ui.holo

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.theme.LcarsColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

private val TAU = (2 * PI).toFloat()
private const val ROW_MS = 110L

// ════════════════════════════════════════════════════════════════════
// GEO-SCAN: gyro-stabilized holographic globe.
// The globe is fixed to the real world (north/east/up) using the rotation
// vector sensor, so tilting/turning the phone moves your view around it.
// The magnetic field and gravity vectors are drawn live in the phone's frame.
// ════════════════════════════════════════════════════════════════════
@Composable
fun GyroGlobe(state: TricorderState, active: Boolean, modifier: Modifier, onTap: () -> Unit = {}) {
    val sweepPhase = rememberPhase(if (active) 0.9f else 0.25f)
    HoloCanvas(modifier, LcarsColors.Orange, onTap) { t, _ ->
        val rot = state.rotation
        val proj = Projector(center, focal = size.minDimension * 1.15f, distance = 3.4f)

        fun w(p: P3): P3 = if (rot != null) p.worldToDevice(rot) else p.rotZ(t * 0.4f).rotX(-1.05f)

        // Outer 2D reticle that slowly rotates
        val rr = size.minDimension * 0.47f
        drawCircle(LcarsColors.Orange.copy(alpha = 0.5f), rr, center, style = Stroke(2f))
        for (i in 0 until 60) {
            val a = i * TAU / 60f + t * 0.15f
            val inner = if (i % 5 == 0) rr * 0.92f else rr * 0.96f
            drawLine(
                LcarsColors.Orange.copy(alpha = if (i % 5 == 0) 0.9f else 0.4f),
                Offset(center.x + inner * cos(a), center.y + inner * sin(a)),
                Offset(center.x + rr * cos(a), center.y + rr * sin(a)),
                strokeWidth = if (i % 5 == 0) 3f else 1.5f,
            )
        }

        // Latitude rings
        for (lat in listOf(-60, -30, 0, 30, 60)) {
            val phi = Math.toRadians(lat.toDouble()).toFloat()
            val ring = (0..48).map { i ->
                val th = i * TAU / 48f
                w(P3(cos(phi) * cos(th), cos(phi) * sin(th), sin(phi)))
            }
            val equator = lat == 0
            holoPolyline(
                proj, ring,
                if (equator) LcarsColors.Orange else LcarsColors.Periwinkle,
                if (equator) 3.5f else 1.6f,
            )
        }
        // Meridians
        for (m in 0 until 12) {
            val th = m * TAU / 12f
            val arc = (0..32).map { i ->
                val phi = -PI.toFloat() / 2 + i * PI.toFloat() / 32f
                w(P3(cos(phi) * cos(th), cos(phi) * sin(th), sin(phi)))
            }
            holoPolyline(proj, arc, LcarsColors.Purple, 1.3f, glow = false)
        }

        // Scanning ring sweeps pole to pole while scanning (slow idle sweep otherwise)
        val sweep = (sweepPhase.value % 1f) * 2f - 1f
        val sPhi = sweep * PI.toFloat() / 2f
        val scanRing = (0..48).map { i ->
            val th = i * TAU / 48f
            w(P3(cos(sPhi) * cos(th) * 1.02f, cos(sPhi) * sin(th) * 1.02f, sin(sPhi) * 1.02f))
        }
        holoPolyline(proj, scanRing, LcarsColors.Yellow, if (active) 4f else 2f, alphaScale = if (active) 1f else 0.6f)

        // Cardinal markers on the horizon
        val labels = listOf("N" to P3(0f, 1.28f, 0f), "E" to P3(1.28f, 0f, 0f), "S" to P3(0f, -1.28f, 0f), "W" to P3(-1.28f, 0f, 0f))
        for ((txt, p) in labels) {
            val d = w(p)
            holoText(
                txt, proj.project(d),
                (if (txt == "N") LcarsColors.Red else LcarsColors.Tan).copy(alpha = proj.depthAlpha(d)),
                size.minDimension * 0.06f,
            )
        }
        val zen = w(P3(0f, 0f, 1.25f))
        holoLine(proj, w(P3(0f, 0f, 1f)), zen, LcarsColors.Ice, 2f)

        // Magnetic field vector (device frame) with arrowhead
        state.magField?.let { m ->
            val len = m.magnitude.coerceAtLeast(0.001f)
            val dir = P3(m.x / len, m.y / len, m.z / len)
            arrow(proj, dir * 1.35f, LcarsColors.Sunset, 4f)
            holoText("B", proj.project(dir * 1.5f), LcarsColors.Sunset, size.minDimension * 0.05f)
        }
        // Gravity ("down") vector: accelerometer measures the reaction, so invert
        state.accel?.let { a ->
            val len = a.magnitude.coerceAtLeast(0.001f)
            val dir = P3(-a.x / len, -a.y / len, -a.z / len)
            arrow(proj, dir * 1.2f, LcarsColors.Ice, 3f)
            holoText("g", proj.project(dir * 1.35f), LcarsColors.Ice, size.minDimension * 0.05f)
        }

        drawCircle(LcarsColors.Orange, size.minDimension * 0.015f, center)
        holoScanlines(LcarsColors.Orange, t)
        holoBrackets(LcarsColors.Orange)
        state.headingDeg?.let {
            holoText(
                "HDG %03d".format(it.toInt()), Offset(size.width * 0.02f, size.height * 0.05f),
                LcarsColors.Yellow, size.minDimension * 0.045f, Paint.Align.LEFT,
            )
        }
        if (rot == null) {
            holoText("NO GYRO FIX", Offset(center.x, size.height * 0.95f), LcarsColors.Red, size.minDimension * 0.04f)
        }
    }
}

private fun DrawScope.arrow(proj: Projector, tip: P3, color: androidx.compose.ui.graphics.Color, width: Float) {
    val origin = P3(0f, 0f, 0f)
    holoLine(proj, origin, tip, color, width)
    // Arrowhead: two short lines back from the tip, perpendicular in screen space
    val pt = proj.project(tip)
    val pb = proj.project(tip * 0.85f)
    val dx = pt.x - pb.x
    val dy = pt.y - pb.y
    val l = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val nx = -dy / l
    val ny = dx / l
    val s = 14f
    drawLine(color, pt, Offset(pb.x + nx * s * 0.6f, pb.y + ny * s * 0.6f), width)
    drawLine(color, pt, Offset(pb.x - nx * s * 0.6f, pb.y - ny * s * 0.6f), width)
}

// ════════════════════════════════════════════════════════════════════
// METEO-SCAN: rotating 3D terrain survey with a live isobar plane.
// Terrain relief scales with light level; the isobar plane rides on pressure.
// ════════════════════════════════════════════════════════════════════
@Composable
fun TerrainScan(state: TricorderState, active: Boolean, modifier: Modifier, onTap: () -> Unit = {}) {
    val beamPhase = rememberPhase(if (active) 0.6f else 0.18f)
    HoloCanvas(modifier, LcarsColors.Periwinkle, onTap) { t, dragYaw ->
        val proj = Projector(Offset(center.x, size.height * 0.55f), focal = size.width * 0.95f, distance = 3.6f)
        val yaw = t * 0.22f + dragYaw
        val pitch = 0.62f
        val n = 18
        val ext = 1.35f
        val relief = 0.45f + (state.lux?.let { log10(it.coerceAtLeast(1f)) / 5f } ?: 0.3f).coerceIn(0f, 1f) * 0.7f

        fun h(x: Float, z: Float): Float =
            (0.17f * sin(1.7f * x + t * 0.35f) * cos(1.3f * z - t * 0.25f) +
                0.09f * sin(3.1f * x + 2.3f * z + t * 0.6f) +
                0.05f * cos(4.7f * z - 1.9f * x)) * relief

        fun v(x: Float, y: Float, z: Float) = P3(x, y - 0.15f, z).rotY(yaw).rotX(pitch)

        val grid = Array(n) { i ->
            Array(n) { j ->
                val x = -ext + 2 * ext * i / (n - 1)
                val z = -ext + 2 * ext * j / (n - 1)
                Triple(x, z, h(x, z))
            }
        }
        val beamZ = -ext + 2 * ext * (beamPhase.value % 1f)

        for (i in 0 until n) for (j in 0 until n) {
            val (x, z, y) = grid[i][j]
            val c = lerpColor(LcarsColors.Blue, LcarsColors.Yellow, (y / (0.3f * relief) + 1f) / 2f)
            val near = kotlin.math.abs(z - beamZ) < ext / n
            if (i + 1 < n) {
                val (x2, z2, y2) = grid[i + 1][j]
                holoLine(proj, v(x, y, z), v(x2, y2, z2), if (near) LcarsColors.Text else c, if (near) 3f else 1.4f, glow = near)
            }
            if (j + 1 < n) {
                val (x2, z2, y2) = grid[i][j + 1]
                holoLine(proj, v(x, y, z), v(x2, y2, z2), c, 1.4f, glow = false)
            }
        }

        // Survey beam: vertical curtain at beamZ
        val top = v(-ext, 0.55f, beamZ)
        val top2 = v(ext, 0.55f, beamZ)
        holoLine(proj, top, top2, LcarsColors.Yellow, 2f, alphaScale = 0.7f)
        holoLine(proj, top, v(-ext, h(-ext, beamZ), beamZ), LcarsColors.Yellow, 1f, glow = false, alphaScale = 0.5f)
        holoLine(proj, top2, v(ext, h(ext, beamZ), beamZ), LcarsColors.Yellow, 1f, glow = false, alphaScale = 0.5f)

        // Isobar plane height from pressure (950..1050 hPa)
        state.pressureHpa?.let { p ->
            val py = 0.05f + ((p - 950f) / 100f).coerceIn(0f, 1f) * 0.5f
            val corners = listOf(v(-ext, py, -ext), v(ext, py, -ext), v(ext, py, ext), v(-ext, py, ext))
            holoPolyline(proj, corners, LcarsColors.Periwinkle, 2f, closed = true, alphaScale = 0.8f)
            holoLine(proj, corners[0], corners[2], LcarsColors.Periwinkle, 1f, glow = false, alphaScale = 0.3f)
            holoLine(proj, corners[1], corners[3], LcarsColors.Periwinkle, 1f, glow = false, alphaScale = 0.3f)
            holoText(
                "ISOBAR %.1f".format(p), proj.project(corners[1]) + Offset(0f, -size.height * 0.04f),
                LcarsColors.Periwinkle, size.minDimension * 0.05f,
            )
        }

        // Light source glyph sized by illuminance
        state.lux?.let { lux ->
            val f = (log10(lux.coerceAtLeast(1f)) / 5f).coerceIn(0f, 1f)
            val sun = Offset(size.width * 0.86f, size.height * 0.13f)
            val r = size.minDimension * (0.025f + 0.04f * f)
            drawCircle(LcarsColors.Yellow.copy(alpha = 0.25f), r * 2.2f, sun)
            drawCircle(LcarsColors.Yellow, r, sun)
            for (k in 0 until 8) {
                val a = k * TAU / 8 + t * 0.5f
                drawLine(
                    LcarsColors.Yellow.copy(alpha = 0.7f),
                    Offset(sun.x + cos(a) * r * 1.4f, sun.y + sin(a) * r * 1.4f),
                    Offset(sun.x + cos(a) * r * 2.1f, sun.y + sin(a) * r * 2.1f), 2f,
                )
            }
        }

        holoScanlines(LcarsColors.Periwinkle, t)
        holoBrackets(LcarsColors.Periwinkle)
    }
}

// ════════════════════════════════════════════════════════════════════
// BIO-SCAN: rotating DNA double helix (spin rate follows motion)
// ════════════════════════════════════════════════════════════════════
@Composable
fun DnaHelix(state: TricorderState, active: Boolean, modifier: Modifier, onTap: () -> Unit = {}) {
    val motion = (state.linearAccel?.magnitude ?: 0f).coerceIn(0f, 4f)
    // Spin speed follows motion but is integrated + eased, so it never jumps.
    val spinPhase = rememberPhase(0.9f + motion * 0.7f + if (active) 0.6f else 0f, smoothing = 1.5f)
    val scanPhase = rememberPhase(if (active) 0.7f else 0f)
    HoloCanvas(modifier, LcarsColors.Lilac, onTap) { t, dragYaw ->
        val proj = Projector(center, focal = size.width * 0.78f, distance = 3.4f)
        val spin = spinPhase.value + dragYaw
        val n = 46
        val r = 0.62f
        val tilt = 0.25f
        fun pt(i: Int, phase: Float): P3 {
            val x = -1.55f + 3.1f * i / (n - 1)
            val a = i * 0.42f + spin + phase
            return P3(x, r * cos(a), r * sin(a)).rotX(tilt).rotZ(0.12f * sin(t * 0.4f))
        }
        val a = (0 until n).map { pt(it, 0f) }
        val b = (0 until n).map { pt(it, PI.toFloat()) }
        val scanX = if (active) (scanPhase.value % 1f) * n else -100f
        val pairs = listOf(LcarsColors.Lilac to LcarsColors.Ice, LcarsColors.Yellow to LcarsColors.Sunset)
        for (i in 0 until n step 2) {
            val (c1, c2) = pairs[(i / 2) % 2]
            val mid = (a[i] + b[i]) * 0.5f
            val lit = kotlin.math.abs(i - scanX) < 3f
            holoLine(proj, a[i], mid, if (lit) LcarsColors.Text else c1, if (lit) 3.5f else 2f, glow = lit)
            holoLine(proj, mid, b[i], if (lit) LcarsColors.Text else c2, if (lit) 3.5f else 2f, glow = lit)
        }
        holoPolyline(proj, a, LcarsColors.Lilac, 3.5f)
        holoPolyline(proj, b, LcarsColors.Purple, 3.5f)
        holoScanlines(LcarsColors.Lilac, t)
        holoBrackets(LcarsColors.Lilac)
    }
}

// ════════════════════════════════════════════════════════════════════
// BIO-SCAN: 3D waterfall of motion history (hidden-line ridges)
// ════════════════════════════════════════════════════════════════════
@Composable
fun MotionWaterfall(state: TricorderState, modifier: Modifier) {
    val history by rememberUpdatedState(state.motionHistory)
    val rows = remember { mutableStateListOf<FloatArray>() }
    val time = rememberHoloTime()
    val lastRowAt = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val h = history
            val row = FloatArray(32) { i ->
                val idx = h.size - 64 + i * 2
                if (idx in h.indices) h[idx] else 0f
            }
            rows.add(0, row)
            if (rows.size > 26) rows.removeAt(rows.lastIndex)
            lastRowAt.floatValue = time.value
            delay(ROW_MS)
        }
    }
    Canvas(modifier) {
        // Fractional scroll between row arrivals keeps the ridges gliding instead of stepping.
        val frac = ((time.value - lastRowAt.floatValue) / (ROW_MS / 1000f)).coerceIn(0f, 1f)
        val proj = Projector(Offset(center.x, size.height * 0.62f), focal = size.width * 0.9f, distance = 3.4f)
        val scale = (rows.maxOfOrNull { r -> r.maxOrNull() ?: 0f } ?: 0f).coerceAtLeast(0.6f)
        // Back to front so nearer ridges occlude farther ones
        for (k in rows.indices.reversed()) {
            val row = rows[k]
            val z = 0.9f - (k + frac) * 0.075f
            val pts = row.indices.map { i ->
                val x = -1.5f + 3f * i / (row.size - 1)
                P3(x, -0.35f + row[i] / scale * 0.75f, z).rotX(0.55f)
            }.map { proj.project(it) }
            val base = proj.project(P3(1.5f, -0.35f, z).rotX(0.55f))
            val base0 = proj.project(P3(-1.5f, -0.35f, z).rotX(0.55f))
            val fill = Path().apply {
                moveTo(base0.x, base0.y)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(base.x, base.y)
                close()
            }
            drawPath(fill, LcarsColors.Black)
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            val alpha = (1f - k / 30f).coerceIn(0.15f, 1f)
            val color = if (k == 0) LcarsColors.Yellow else LcarsColors.Lilac
            drawPath(line, color.copy(alpha = alpha), style = Stroke(if (k == 0) 3f else 2f))
        }
        holoBrackets(LcarsColors.Purple)
    }
}

// ════════════════════════════════════════════════════════════════════
// ENG-SCAN: rotating power cell. Fill level = charge, color = temperature,
// plasma particles rise faster while charging, arcs flash when on power.
// ════════════════════════════════════════════════════════════════════
@Composable
fun PowerCell(state: TricorderState, active: Boolean, modifier: Modifier, onTap: () -> Unit = {}) {
    val bat = state.battery
    val isCharging = bat?.status == "CHARGING" || bat?.powerSource?.let { it != "INTERNAL CELL" } == true
    val yawPhase = rememberPhase(if (active) 1.2f else 0.45f)
    val plasmaPhase = rememberPhase(if (isCharging) 0.55f else 0.18f)
    HoloCanvas(modifier, LcarsColors.Yellow, onTap) { t, dragYaw ->
        val b = state.battery
        val level = ((b?.levelPercent ?: 0).coerceAtLeast(0)) / 100f
        val charging = b?.status == "CHARGING" || b?.powerSource?.let { it != "INTERNAL CELL" } == true
        val temp = b?.temperatureC ?: 30f
        val heat = lerpColor(LcarsColors.Ice, LcarsColors.Red, (temp - 25f) / 20f)
        val proj = Projector(center, focal = size.minDimension * 1.2f, distance = 3.5f)
        val yaw = yawPhase.value + dragYaw
        fun v(p: P3) = p.rotY(yaw).rotX(0.38f)

        val r = 0.62f
        val h0 = -0.95f
        val h1 = 0.95f
        val fillY = h0 + (h1 - h0) * level
        val segs = 40

        fun ring(y: Float, rad: Float = r) = (0..segs).map { i ->
            val a = i * TAU / segs
            v(P3(rad * cos(a), y, rad * sin(a)))
        }

        // Shell rings
        for (k in 0..10) {
            val y = h0 + (h1 - h0) * k / 10f
            val filled = y <= fillY
            holoPolyline(
                proj, ring(y),
                if (filled) heat else LcarsColors.Dim,
                if (filled) 2.6f else 1.2f, glow = filled,
            )
        }
        // Struts
        for (k in 0 until 12) {
            val a = k * TAU / 12
            holoLine(proj, v(P3(r * cos(a), h0, r * sin(a))), v(P3(r * cos(a), h1, r * sin(a))), LcarsColors.Yellow, 1.4f, glow = false, alphaScale = 0.6f)
        }
        // Electrodes
        holoPolyline(proj, ring(h1 + 0.12f, 0.22f), LcarsColors.Orange, 3f)
        holoLine(proj, v(P3(0f, h1, 0f)), v(P3(0f, h1 + 0.12f, 0f)), LcarsColors.Orange, 4f)
        // Energy surface (wavy top of the charge)
        val surf = (0..segs).map { i ->
            val a = i * TAU / segs
            v(P3(r * cos(a), fillY + 0.03f * sin(a * 3 + t * 4f), r * sin(a)))
        }
        holoPolyline(proj, surf, LcarsColors.Text, 3f)

        // Plasma particles
        for (i in 0 until 22) {
            val ph = ((plasmaPhase.value + i / 22f) % 1f)
            val y = h0 + (fillY - h0) * ph
            val a = i * 2.39996f + t * 0.8f
            val rad = r * (0.25f + 0.6f * ((i * 0.37f) % 1f))
            val p = v(P3(rad * cos(a), y, rad * sin(a)))
            drawCircle(heat.copy(alpha = proj.depthAlpha(p)), 4.5f, proj.project(p))
        }

        // Arcs between electrode and shell when on external power
        if (charging) {
            val seed = floor(t * 12f).toInt()
            for (k in 0 until 2) {
                val a0 = ((seed * 37 + k * 91) % 360) * TAU / 360f
                val start = P3(0.22f * cos(a0), h1 + 0.12f, 0.22f * sin(a0))
                val end = P3(r * cos(a0 + 0.6f), h1 - 0.25f, r * sin(a0 + 0.6f))
                var prev = v(start)
                for (s in 1..6) {
                    val f = s / 6f
                    val j = if (s < 6) (((seed + s * 13 + k * 7) % 10) / 10f - 0.5f) * 0.18f else 0f
                    val cur = v(P3(start.x + (end.x - start.x) * f + j, start.y + (end.y - start.y) * f, start.z + (end.z - start.z) * f - j))
                    holoLine(proj, prev, cur, LcarsColors.Ice, 2f)
                    prev = cur
                }
            }
        }

        holoText(
            "${(level * 100).toInt()}%", proj.project(v(P3(0f, fillY, 0f))) + Offset(0f, -size.height * 0.02f),
            LcarsColors.Text, size.minDimension * 0.08f,
        )
        holoScanlines(LcarsColors.Yellow, t)
        holoBrackets(LcarsColors.Yellow)
    }
}
